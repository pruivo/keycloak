/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;
import org.infinispan.protostream.annotations.ProtoTypeId;
import org.keycloak.marshalling.Marshalling;
import org.keycloak.models.ClientModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.cache.infinispan.RealmCacheManager;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@ProtoTypeId(Marshalling.CLIENT_EVENT)
public class ClientEvent extends TypedInvalidationEvent implements RealmCacheInvalidationEvent {

    private final String clientUuid;
    private final String clientId;
    private final String realmId;
    // roleId -> roleName
    private final Map<String, String> clientRoles;

    private ClientEvent(Type eventType, String clientUuid, String clientId, String realmId, Map<String, String> clientRoles) {
        super(eventType);
        this.clientUuid = Objects.requireNonNull(clientUuid);
        this.clientId = clientId;
        this.realmId = Objects.requireNonNull(realmId);
        this.clientRoles = clientRoles;
    }

    @ProtoFactory
    static ClientEvent protoFactory(String id, Type eventType, String clientId, String realmId, Map<String, String> clientRoles) {
        return new ClientEvent(eventType, id, Marshalling.emptyStringToNull(clientId), realmId, clientRoles);
    }

    public static ClientEvent added(String clientUuid, String realmId) {
        return new ClientEvent(Type.ADDED, clientUuid, null, realmId, null);
    }

    public static ClientEvent removed(ClientModel client) {
        return new ClientEvent(Type.REMOVED, client.getId(), client.getClientId(), client.getRealm().getId(),
                client.getRolesStream().collect(Collectors.toMap(RoleModel::getId, RoleModel::getName)));
    }

    public static ClientEvent updated(String clientUuid, String clientId, String realmId) {
        return new ClientEvent(Type.UPDATED, clientUuid, clientId, realmId, null);
    }

    @Override
    public String getId() {
        return clientUuid;
    }

    @ProtoField(3)
    String getClientId() {
        return clientId;
    }

    @ProtoField(4)
    String getRealmId() {
        return realmId;
    }

    @ProtoField(5)
    Map<String, String> getClientRoles() {
        return clientRoles;
    }

    @Override
    public String toString() {
        return String.format("ClientAddedEvent [ realmId=%s, clientUuid=%s, clientId=%s, clientRoles=%s, type=%s ]", realmId, clientUuid, clientId, clientRoles, eventType());
    }

    @Override
    public void addInvalidations(RealmCacheManager realmCache, Set<String> invalidations) {
        switch (eventType()) {
            case ADDED -> realmCache.clientAdded(realmId, invalidations);
            case REMOVED -> {
                realmCache.clientRemoval(realmId, clientUuid, clientId, invalidations);

                // Separate iteration for all client roles to invalidate records dependent on them
                for (Map.Entry<String, String> clientRole : clientRoles.entrySet()) {
                    String roleId = clientRole.getKey();
                    String roleName = clientRole.getValue();
                    realmCache.roleRemoval(roleId, roleName, clientUuid, invalidations);
                }
            }
            case UPDATED -> realmCache.clientUpdated(realmId, clientUuid, clientId, invalidations);
        }

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
        ClientEvent that = (ClientEvent) o;
        return Objects.equals(clientId, that.clientId) && Objects.equals(realmId, that.realmId) && Objects.equals(clientRoles, that.clientRoles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), clientId, realmId, clientRoles);
    }

}
