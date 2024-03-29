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

import java.util.Objects;
import java.util.Set;

import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;
import org.infinispan.protostream.annotations.ProtoTypeId;
import org.keycloak.marshalling.Marshalling;
import org.keycloak.models.cache.infinispan.RealmCacheManager;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@ProtoTypeId(Marshalling.ROLE_ADDED_EVENT)
public class RoleEvent extends TypedInvalidationEvent implements RealmCacheInvalidationEvent {

    private final String roleId;
    private final String roleName;
    private final String containerId;

    private RoleEvent(Type eventType, String roleId, String roleName, String containerId) {
        super(eventType);
        this.roleId = Objects.requireNonNull(roleId);
        this.roleName = roleName;
        this.containerId = Objects.requireNonNull(containerId);
    }

    @ProtoFactory
    static RoleEvent protoFactory(String id, Type eventType, String roleName, String containerId) {
        return new RoleEvent(eventType, id, Marshalling.emptyStringToNull(roleName), containerId);
    }

    public static RoleEvent added(String roleId, String containerId) {
        return new RoleEvent(Type.ADDED, roleId, null, containerId);
    }

    public static RoleEvent removed(String roleId, String roleName, String containerId) {
        return new RoleEvent(Type.REMOVED, roleId, Objects.requireNonNull(roleName), containerId);
    }

    public static RoleEvent updated(String roleId, String roleName, String containerId) {
        return new RoleEvent(Type.UPDATED, roleId, Objects.requireNonNull(roleName), containerId);
    }

    @Override
    public String getId() {
        return roleId;
    }

    @ProtoField(3)
    String getRoleName() {
        return roleName;
    }

    @ProtoField(4)
    String getContainerId() {
        return containerId;
    }


    @Override
    public String toString() {
        return String.format("RoleAddedEvent [ roleId=%s, roleName=%s, containerId=%s, type=%s ]", roleId, roleName, containerId, eventType());
    }

    @Override
    public void addInvalidations(RealmCacheManager realmCache, Set<String> invalidations) {
        switch (eventType()) {
            case ADDED -> realmCache.roleAdded(containerId, invalidations);
            case REMOVED -> realmCache.roleRemoval(roleId, roleName, containerId, invalidations);
            case UPDATED -> realmCache.roleUpdated(containerId, roleName, invalidations);
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
        RoleEvent that = (RoleEvent) o;
        return Objects.equals(roleName, that.roleName) && Objects.equals(containerId, that.containerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), roleId, roleName, containerId);
    }

}
