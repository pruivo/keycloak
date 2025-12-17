/*
 * Copyright 2025 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.infinispan.module.persistence;

import java.util.function.BiConsumer;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

import static org.keycloak.infinispan.module.persistence.CacheLoadEvent.NAME;

@Name(NAME)
@Label("Database Load")
@Description("An event trigger when Infinispan reads an entry from the database")
@Category({"Keycloak", "Infinispan", "Persistence"})
@StackTrace(false)
public class CacheLoadEvent<V> extends Event implements BiConsumer<V, Throwable> {
    public static final String NAME = "org.keycloak.infinispan.persistence.CacheLoad";

    @Label("Cache Name")
    String cacheName;
    @Label("Cache Key")
    String key;
    @Label("Successful")
    boolean success;
    @Label("Value Found")
    boolean hit;

    public CacheLoadEvent(String cacheName, String key) {
        this.cacheName = cacheName;
        this.key = key;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setHit(boolean hit) {
        this.hit = hit;
    }

    @Override
    public void accept(V value, Throwable throwable) {
        if (!isEnabled() || !shouldCommit()) {
            return;
        }
        setSuccess(throwable == null);
        setHit(value != null);
        commit();
    }
}
