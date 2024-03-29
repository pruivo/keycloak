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

package org.keycloak.models.cache.infinispan.authorization.events;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;
import org.infinispan.protostream.annotations.ProtoTypeId;
import org.keycloak.marshalling.Marshalling;
import org.keycloak.models.cache.infinispan.authorization.StoreFactoryCacheManager;
import org.keycloak.models.cache.infinispan.events.TypedInvalidationEvent;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@ProtoTypeId(Marshalling.POLICY_EVENT)
public class PolicyEvent extends TypedInvalidationEvent implements AuthorizationCacheInvalidationEvent {

    private final String id;
    private final String name;
    private final Set<String> resources;
    private final Set<String> resourceTypes;
    private final Set<String> scopes;
    private final String serverId;

    @ProtoFactory
    PolicyEvent(String id, Type eventType, String name, Set<String> resources, Set<String> resourceTypes, Set<String> scopes, String serverId) {
        super(eventType);
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.resources = resources;
        this.resourceTypes = resourceTypes;
        this.scopes = scopes;
        this.serverId = Objects.requireNonNull(serverId);
    }

    public static PolicyEvent removed(String id, String name, Set<String> resources, Set<String> resourceTypes, Set<String> scopes, String serverId) {
        return new PolicyEvent(id, Type.REMOVED, name, resources, resourceTypes, scopes, serverId);
    }

    public static PolicyEvent updated(String id, String name, Set<String> resources, Set<String> resourceTypes, Set<String> scopes, String serverId) {
        return new PolicyEvent(id, Type.UPDATED, name, resources, resourceTypes, scopes, serverId);
    }

    @Override
    public String getId() {
        return id;
    }

    @ProtoField(3)
    String getName() {
        return name;
    }

    @ProtoField(value = 4, collectionImplementation = HashSet.class)
    Set<String> getResources() {
        return resources;
    }

    @ProtoField(value = 5, collectionImplementation = HashSet.class)
    Set<String> getResourceTypes() {
        return resourceTypes;
    }

    @ProtoField(value = 6, collectionImplementation = HashSet.class)
    Set<String> getScopes() {
        return scopes;
    }

    @ProtoField(7)
    String getServerId() {
        return serverId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof PolicyEvent that &&
                super.equals(that) &&
                Objects.equals(name, that.name) &&
                Objects.equals(serverId, that.serverId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id, name, serverId);
    }

    @Override
    public String toString() {
        return String.format("PolicyEvent [id=%s, name=%s, type=%s]", id, name, eventType());
    }

    @Override
    public void addInvalidations(StoreFactoryCacheManager cache, Set<String> invalidations) {
        switch (eventType()) {
            case UPDATED -> cache.policyUpdated(id, name, resources, resourceTypes, scopes, serverId, invalidations);
            case REMOVED -> cache.policyRemoval(id, name, resources, resourceTypes, scopes, serverId, invalidations);
        }
    }

}
