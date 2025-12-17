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

import static org.keycloak.infinispan.module.persistence.CacheClearEvent.NAME;

@Name(NAME)
@Label("Database Clear")
@Description("An event trigger every time Infinispan clears the database table")
@Category({"Keycloak", "Infinispan", "Persistence"})
@StackTrace(false)
public class CacheClearEvent extends Event implements BiConsumer<Void, Throwable> {
    public static final String NAME = "org.keycloak.infinispan.persistence.CacheClear";

    @Label("Cache Name")
    String cacheName;

    public CacheClearEvent(String cacheName) {
        this.cacheName = cacheName;
    }

    @Override
    public void accept(Void unused, Throwable throwable) {
        if (!isEnabled() || !shouldCommit()) {
            return;
        }
        commit();
    }
}
