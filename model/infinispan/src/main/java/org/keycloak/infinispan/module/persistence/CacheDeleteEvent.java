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

import static org.keycloak.infinispan.module.persistence.CacheDeleteEvent.NAME;

@Name(NAME)
@Label("Database Store")
@Description("An event trigger every time Infinispan deletes an entry from database")
@Category({"Keycloak", "Infinispan", "Persistence"})
@StackTrace(false)
public class CacheDeleteEvent extends Event implements BiConsumer<Boolean, Throwable> {
    public static final String NAME = "org.keycloak.infinispan.persistence.CacheDelete";

    @Label("Cache Name")
    String cacheName;
    @Label("Cache Key")
    String key;
    @Label("Successful")
    boolean success;
    @Label("Removed")
    boolean removed;

    public CacheDeleteEvent(String cacheName, String key) {
        this.cacheName = cacheName;
        this.key = key;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }

    @Override
    public void accept(Boolean removed, Throwable throwable) {
        if (!isEnabled() || !shouldCommit()) {
            return;
        }
        setSuccess(throwable == null);
        if (removed == Boolean.TRUE) {
            setRemoved(true);
        }
        commit();
    }
}
