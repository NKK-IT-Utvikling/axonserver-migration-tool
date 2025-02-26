/*
 * Copyright (c) 2010-2023. AxonIQ
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.axoniq.axonserver.migration.source.jpa.terminliste;


import io.axoniq.axonserver.migration.MigrationContext;
import io.axoniq.axonserver.migration.source.DomainEvent;
import io.axoniq.axonserver.migration.source.EventProducer;
import io.axoniq.axonserver.migration.source.SnapshotEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

/**
 * Produces events when defining the migration source as {@code RDBMS}. Queries the database using JPA to look up events
 * and snapshots, that can then be migrated.
 *
 * @author Marc Gathier
 * @author Mitchell Herrijgers
 */
@Component
@ConditionalOnProperty(value = "axoniq.migration.context", havingValue = "TERMINLISTE")
@Transactional(readOnly = true, transactionManager = "eventStoreTransactionManager")
public class TerminlisteJpaEventProducer implements EventProducer {

    @PersistenceContext(name = "eventstore")
    private EntityManager entityManager;


    @Override
    public List<? extends DomainEvent> findEvents(long lastProcessedToken, int batchSize) {
        return entityManager.createNamedQuery("TerminlisteDomainEventEntry.findByGlobalIndex", TerminlisteDomainEventEntry.class)
                            .setParameter("lastToken", lastProcessedToken)
                            .setMaxResults(batchSize)
                            .getResultList();
    }

    @Override
    public List<? extends SnapshotEvent> findSnapshots(String lastProcessedTimestamp, int batchSize) {
        return entityManager.createNamedQuery("TerminlisteSnapshotEventEntry.findByTimestamp", TerminlisteSnapshotEventEntry.class)
                            .setParameter("lastTimeStamp", lastProcessedTimestamp)
                            .setMaxResults(batchSize)
                            .getResultList();
    }

    @Override
    public long getMinIndex() {
        return entityManager.createNamedQuery("TerminlisteDomainEventEntry.minGlobalIndex", Long.class)
                            .getSingleResult();
    }

    @Override
    public long getMaxIndex() {
        return entityManager.createNamedQuery("TerminlisteDomainEventEntry.maxGlobalIndex", Long.class)
                            .getSingleResult();
    }

    @Override
    public MigrationContext getContext() {
        return MigrationContext.TERMINLISTE;
    }
}
