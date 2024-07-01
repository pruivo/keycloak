/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.models.sessions.infinispan.entities;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;
import org.infinispan.protostream.annotations.ProtoTypeId;
import org.keycloak.marshalling.Marshalling;

/**
 *
 * @author hmlnarik
 */
@ProtoTypeId(Marshalling.AUTHENTICATED_CLIENT_SESSION_STORE)
public class AuthenticatedClientSessionStore {

    /**
     * Maps client UUID to client session ID.
     */
    private final ConcurrentMap<String, String> authenticatedClientSessionIds;

    public AuthenticatedClientSessionStore() {
        authenticatedClientSessionIds = new ConcurrentHashMap<>();
    }

    @ProtoFactory
    AuthenticatedClientSessionStore(ConcurrentMap<String, String> authenticatedClientSessionIds) {
        this.authenticatedClientSessionIds = authenticatedClientSessionIds;
    }

    public void clear() {
        authenticatedClientSessionIds.clear();
    }

    public boolean containsKey(String key) {
        return authenticatedClientSessionIds.containsKey(key);
    }

    public void forEach(BiConsumer<? super String, ? super String> action) {
        authenticatedClientSessionIds.forEach(action);
    }

    public String get(String key) {
        return authenticatedClientSessionIds.get(key);
    }

    public Set<String> keySet() {
        return authenticatedClientSessionIds.keySet();
    }

    public String put(String key, String value) {
        return authenticatedClientSessionIds.put(key, value);
    }

    public String remove(String clientUUID) {
        return authenticatedClientSessionIds.remove(clientUUID);
    }

    public int size() {
        return authenticatedClientSessionIds.size();
    }

    @ProtoField(value = 1, mapImplementation = ConcurrentHashMap.class)
    ConcurrentMap<String, String> getAuthenticatedClientSessionIds() {
        return authenticatedClientSessionIds;
    }

    @Override
    public String toString() {
        return this.authenticatedClientSessionIds.toString();
    }

}
