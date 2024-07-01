/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.models.sessions.infinispan.changes.remote;

import java.util.Map;

import org.infinispan.client.hotrod.MetadataValue;
import org.keycloak.models.sessions.infinispan.changes.remote.updater.Updater;

public interface RemoveEntryPredicate<K, V> {

    boolean remove(K key, V value);

    default boolean remove(Map.Entry<K, MetadataValue<V>> entry) {
        return remove(entry.getKey(), entry.getValue().getValue());
    }

    default boolean remove(Updater<K, V> updater) {
        return remove(updater.getKey(), updater.getValue());
    }

}
