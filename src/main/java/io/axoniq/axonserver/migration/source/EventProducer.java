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

package io.axoniq.axonserver.migration.source;

import io.axoniq.axonserver.migration.MigrationContext;

import java.util.List;

/**
 *
 *
 * @author Marc Gathier
 * @author Mitchell Herrijgers
 */
public interface EventProducer {

    List<? extends DomainEvent> findEvents(long lastProcessedToken, int batchSize);

    List<? extends SnapshotEvent> findSnapshots(String lastProcessedTimestamp, int batchSize);

    default long getMaxIndex() {
        return -1; // -1 indicates not supported
    }

    default long getMinIndex() {
        return -1; // -1 indicates not supported
    }

    MigrationContext getContext();
}
