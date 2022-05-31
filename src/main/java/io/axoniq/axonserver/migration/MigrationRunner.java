package io.axoniq.axonserver.migration;


import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.migration.db.MigrationStatus;
import io.axoniq.axonserver.migration.db.MigrationStatusRepository;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.util.GrpcMetaDataConverter;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedMetaData;
import org.axonframework.serialization.Serializer;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PreDestroy;


/**
 * @author Marc Gathier
 */
@Profile("!test")
@Component
public class MigrationRunner implements CommandLineRunner {

    private final EventProducer eventProducer;
    private final AxonServerConnectionManager axonDBClient;
    private final Serializer serializer;
    private final MigrationStatusRepository migrationStatusRepository;
    private final Logger logger = LoggerFactory.getLogger(MigrationRunner.class);
    private final GrpcMetaDataConverter grpcMetaDataConverter;

    @Value("${axoniq.migration.batchSize:100}")
    private int batchSize;
    @Value("${axoniq.migration.recentMillis:10000}")
    private int recentMillis;

    @Value("${axoniq.migration.migrateSnapshots:true}")
    private boolean migrateSnapshots;

    @Value("${axoniq.migration.migrateEvents:true}")
    private boolean migrateEvents;

    @Value("${axoniq.migration.ignoredEvents:}")
    private List<String> ignoredEvents;

    private final AtomicLong snapshotsMigrated = new AtomicLong();
    private final AtomicLong eventsMigrated = new AtomicLong();
    private final AtomicLong lastReported = new AtomicLong();
    private final ApplicationContext context;

    private final DB db = DBMaker.fileDB("skipped_events.db").fileMmapEnable().make();
    private final Map<String, Integer> skippedEventsMap = db
            .hashMap("map", org.mapdb.Serializer.STRING, org.mapdb.Serializer.INTEGER)
            .createOrOpen();

    public MigrationRunner(EventProducer eventProducer, Serializer serializer,
                           MigrationStatusRepository migrationStatusRepository,
                           AxonServerConnectionManager axonDBClient,
                           ApplicationContext context) {
        this.eventProducer = eventProducer;
        this.axonDBClient = axonDBClient;
        this.serializer = serializer;
        this.migrationStatusRepository = migrationStatusRepository;
        this.context = context;
        this.grpcMetaDataConverter = new GrpcMetaDataConverter(this.serializer);
    }

    @PreDestroy
    public void destroy() {
        db.close();
    }

    @Override
    public void run(String... options) {
        try {
            migrateEvents();
            migrateSnapshots();
            logger.info("Migration completed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Migration interrupted");
        } catch (ExecutionException executionException) {
            logger.error("Error during migration", executionException.getCause());
        } catch (TimeoutException e) {
            logger.error("Error during migration", e);
        } finally {
            logger.info("Migrated {} events and {} snapshots", eventsMigrated.get(), snapshotsMigrated.get());
            SpringApplication.exit(context);
        }
    }

    private void migrateSnapshots() throws InterruptedException, ExecutionException, TimeoutException {
        if (!migrateSnapshots) {
            logger.info("Skipping migration of snapshots due to configuration");
            return;
        }
        MigrationStatus migrationStatus = migrationStatusRepository.findById(1L).orElse(new MigrationStatus());

        String lastProcessedTimestamp = migrationStatus.getLastSnapshotTimestamp();
        String lastEventId = migrationStatus.getLastSnapshotEventId();

        boolean keepRunning = true;

        logger.info("Starting migration of snapshots from timestamp: {}, batchSize = {}",
                lastProcessedTimestamp,
                batchSize);

        try {
            while(keepRunning) {
                keepRunning = false;
                List<? extends SnapshotEvent> result = eventProducer.findSnapshots(lastProcessedTimestamp, batchSize);
                if (result.isEmpty()) {
                    logger.info("No more snapshots found");
                    return;
                }
                boolean lastFound = (lastEventId == null);
                for (SnapshotEvent entry : result) {
                    if (!lastFound ) {
                        lastFound = entry.getTimeStamp().compareTo(lastProcessedTimestamp) > 0 || entry.getEventIdentifier().equals(lastEventId);
                        continue;
                    }

                    Event.Builder eventBuilder = Event.newBuilder().setAggregateIdentifier(entry.getAggregateIdentifier())
                                                      .setPayload(toPayload(entry))
                                                      .setAggregateSequenceNumber(entry.getSequenceNumber())
                                                      .setMessageIdentifier(entry.getEventIdentifier())
                                                      .setAggregateType(entry.getType());

                    eventBuilder.setTimestamp(entry.getTimeStampAsLong());
                    convertMetadata(entry.getMetaData(), eventBuilder);

                    axonDBClient.getConnection().eventChannel().appendSnapshot(eventBuilder.build()).get(30,
                                                                                                         TimeUnit.SECONDS);
                    lastProcessedTimestamp = entry.getTimeStamp();
                    lastEventId = entry.getEventIdentifier();
                    if (snapshotsMigrated.incrementAndGet() % 1000 == 0) {
                        logger.debug("Migrated {} snapshots", snapshotsMigrated.get());
                    }
                    keepRunning = true;
                }
            }
        } finally {
            migrationStatus.setLastSnapshotEventId(lastEventId);
            migrationStatus.setLastSnapshotTimestamp(lastProcessedTimestamp);
            migrationStatusRepository.save(migrationStatus);
        }


    }

    private void convertMetadata(byte[] metadataBytes, Event.Builder eventBuilder) {
        if (metadataBytes != null) {
            MetaData metaData = serializer.deserialize(new SerializedMetaData<>(metadataBytes, byte[].class));
            Map<String, MetaDataValue> metaDataValues = new HashMap<>();
            metaData.forEach((k, v) -> metaDataValues.put(k, grpcMetaDataConverter.convertToMetaDataValue(v)));
            eventBuilder.putAllMetaData(metaDataValues);
        }
    }

    private void migrateEvents() throws ExecutionException, InterruptedException, TimeoutException {
        if (!migrateEvents) {
            logger.info("Skipping migration of events due to configuration");
            return;
        }
        MigrationStatus migrationStatus = migrationStatusRepository.findById(1L).orElse(new MigrationStatus());

        long lastProcessedToken = migrationStatus.getLastEventGlobalIndex();
        boolean keepRunning = true;

        logger.info("Starting migration of event from globalIndex: {}, batchSize = {}", lastProcessedToken, batchSize);

        while (keepRunning) {
            List<? extends DomainEvent> result = eventProducer.findEvents(lastProcessedToken, batchSize);
            if (result.isEmpty()) {
                logger.info("No more events found");
                return;
            }
            DomainEvent lastEntry = result.get(result.size() - 1);
            lastProcessedToken = lastEntry.getGlobalIndex();

            List<Event> events = new ArrayList<>();
            for (DomainEvent entry : result) {
                if (ignoredEvents.contains(entry.getPayloadType())) {
                    if (entry.getType() != null) {
                        // Increase the skip count
                        Integer currentSkip = skippedEventsMap.getOrDefault(entry.getAggregateIdentifier(), 0);
                        skippedEventsMap.put(entry.getAggregateIdentifier(), currentSkip + 1);
                    }
                    continue;
                }
                if (entry.getGlobalIndex() != lastProcessedToken + 1 && recentEvent(entry)) {
                    logger.error("Missing event at: {}, found globalIndex {}, stopping migration",
                                 (lastProcessedToken + 1),
                                 entry.getGlobalIndex());
                    keepRunning = false;
                    break;
                }

                Event event = buildEvent(entry);
                events.add(event);
            }

            storeEvents(events);

            if (eventsMigrated.addAndGet(events.size()) > lastReported.get() + 1000) {
                lastReported.set(eventsMigrated.intValue());
                logger.debug("Migrated {} events", eventsMigrated.get());
            }
            migrationStatus.setLastEventGlobalIndex(lastProcessedToken);
            migrationStatusRepository.save(migrationStatus);
            db.commit();
        }
    }

    private Event buildEvent(final DomainEvent entry) {
        Event.Builder eventBuilder = Event.newBuilder()
                                          .setPayload(toPayload(entry))
                                          .setMessageIdentifier(entry.getEventIdentifier());

        if (entry.getType() != null) {
            Integer currentSkip = skippedEventsMap.getOrDefault(entry.getAggregateIdentifier(), 0);
            eventBuilder.setAggregateType(entry.getType())
                        .setAggregateSequenceNumber(entry.getSequenceNumber() - currentSkip)
                        .setAggregateIdentifier(entry.getAggregateIdentifier());
        }

        eventBuilder.setTimestamp(entry.getTimeStampAsLong());
        convertMetadata(entry.getMetaData(), eventBuilder);
        return eventBuilder.build();
    }

    private void storeEvents(List<Event> events) throws ExecutionException, InterruptedException, TimeoutException {
        if (events.isEmpty()) {
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Store {} events", events.size());
        }
        try {
            axonDBClient.getConnection()
                        .eventChannel()
                        .appendEvents(events.toArray(new Event[0]))
                        .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e.getMessage() == null || !e.getMessage().contains("OUT_OF_RANGE")) {
                throw e;
            }
            // Out of range error. Skip if batch size is one, reduce batch sizes otherwise
            if (events.size() == 1) {
                logger.warn("Event is probably already migrated, skipping...  Message from server: {}", e.getMessage());
            } else {
                // Batch was greater than one, the entire batch failed. Split the list and retry
                logger.warn("One of the events in the batch was OUT_OF_RANGE. Retrying with batches of 1...");
                for (Event event : events) {
                    storeEvents(Collections.singletonList(event));
                }
            }

        }
    }

    private boolean recentEvent(DomainEvent entry) {
        return entry.getTimeStampAsLong() > System.currentTimeMillis() - recentMillis;
    }

    private SerializedObject toPayload(BaseEvent entry) {
        SerializedObject.Builder builder = SerializedObject.newBuilder()
                .setData(ByteString.copyFrom(entry.getPayload()))
                .setType(entry.getPayloadType());

        if( entry.getPayloadRevision() != null)
            builder.setRevision(entry.getPayloadRevision());
        return builder.build();
    }
}
