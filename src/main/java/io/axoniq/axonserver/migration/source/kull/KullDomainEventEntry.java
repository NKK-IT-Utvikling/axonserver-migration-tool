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

package io.axoniq.axonserver.migration.source.kull;

import io.axoniq.axonserver.migration.source.DomainEvent;
import io.axoniq.axonserver.migration.source.jpa.BaseEventEntry;

import javax.persistence.*;

/**
 * Entity representing an event in the RDBMS store.
 *
 * @author Marc Gathier
 */
@Entity
@Table(
        name = "kull_domainevent",
        indexes = {@Index(
                columnList = "aggregateIdentifier,sequenceNumber",
                unique = true
        )}
)
@NamedQuery(name = "KullDomainEventEntry.findByGlobalIndex", query = "select e from KullDomainEventEntry e where e.globalIndex > :lastToken order by e.globalIndex asc")
@NamedQuery(name = "KullDomainEventEntry.maxGlobalIndex", query = "select max(e.globalIndex) from KullDomainEventEntry e")
@NamedQuery(name = "KullDomainEventEntry.minGlobalIndex", query = "select min(e.globalIndex) from KullDomainEventEntry e")
public class KullDomainEventEntry extends BaseEventEntry implements DomainEvent {
    @Id
    @GeneratedValue
    private long globalIndex;

    @Basic
    private String type;
    @Basic(optional = false)
    private String aggregateIdentifier;
    @Basic(optional = false)
    private long sequenceNumber;

    public long getGlobalIndex() {
        return globalIndex;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    @Override
    public long getSequenceNumber() {
        return sequenceNumber;
    }

}
