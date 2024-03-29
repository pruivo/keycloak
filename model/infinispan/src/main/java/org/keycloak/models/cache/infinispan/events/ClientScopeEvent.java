/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.models.cache.infinispan.events;

import java.util.Objects;
import java.util.Set;

import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;
import org.infinispan.protostream.annotations.ProtoTypeId;
import org.keycloak.marshalling.Marshalling;
import org.keycloak.models.cache.infinispan.RealmCacheManager;

@ProtoTypeId(Marshalling.CLIENT_SCOPE_EVENT)
public class ClientScopeEvent extends TypedInvalidationEvent implements RealmCacheInvalidationEvent {

    private final String clientScopeId;
    private final String realmId;

    @ProtoFactory
    ClientScopeEvent(String id, Type eventType, String realmId) {
        super(eventType);
        this.clientScopeId = id;
        this.realmId = realmId;
    }

    public static ClientScopeEvent added(String clientScopeId, String realmId) {
        return new ClientScopeEvent(clientScopeId, Type.ADDED, realmId);
    }

    public static ClientScopeEvent removed(String clientScopeId, String realmId) {
        return new ClientScopeEvent(clientScopeId, Type.REMOVED, realmId);
    }

    @Override
    public String getId() {
        return clientScopeId;
    }


    @ProtoField(3)
    String getRealmId() {
        return realmId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ClientScopeEvent that = (ClientScopeEvent) o;
        return Objects.equals(realmId, that.realmId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), realmId);
    }

    @Override
    public String toString() {
        return String.format("ClientScopeAddedEvent [ clientScopeId=%s, realmId=%s, eventType=%s ]", clientScopeId, realmId, eventType());
    }

    @Override
    public void addInvalidations(RealmCacheManager realmCache, Set<String> invalidations) {
        switch (eventType()) {
            case ADDED -> realmCache.clientScopeAdded(realmId, invalidations);
            case REMOVED -> realmCache.clientScopeRemoval(realmId, invalidations);
        }

    }

}
