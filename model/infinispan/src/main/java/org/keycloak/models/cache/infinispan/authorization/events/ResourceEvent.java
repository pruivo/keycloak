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
@ProtoTypeId(Marshalling.RESOURCE_EVENT)
public class ResourceEvent extends TypedInvalidationEvent implements AuthorizationCacheInvalidationEvent {

    private final String id;
    private final String name;
    private final String owner;
    private final String serverId;
    private final String type;
    private final Set<String> uris;
    private final Set<String> scopes;

    private ResourceEvent(String id, Type eventType, String name, String owner, String serverId, String type, Set<String> uris, Set<String> scopes) {
        super(eventType);
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.owner = Objects.requireNonNull(owner);
        this.serverId = Objects.requireNonNull(serverId);
        this.type = type;
        this.uris = uris;
        this.scopes = scopes;
    }

    @ProtoFactory
    static ResourceEvent factory(String id, Type eventType, String name, String type, Set<String> uris, String owner, Set<String> scopes, String serverId) {
        return new ResourceEvent(id, eventType, name, owner, serverId, Marshalling.emptyStringToNull(type), uris, scopes);
    }

    public static ResourceEvent removed(String id, String name, String type, Set<String> uris, String owner, Set<String> scopes, String serverId) {
        return new ResourceEvent(id, Type.REMOVED, name, owner, serverId, type, uris, scopes);
    }

    public static ResourceEvent updated(String id, String name, String type, Set<String> uris, String owner, Set<String> scopes, String serverId) {
        return new ResourceEvent(id, Type.UPDATED, name, owner, serverId, type, uris, scopes);
    }

    @Override
    public String getId() {
        return id;
    }

    @ProtoField(3)
    String getName() {
        return name;
    }

    @ProtoField(4)
    String getType() {
        return type;
    }

    @ProtoField(value = 5, collectionImplementation = HashSet.class)
    Set<String> getUris() {
        return uris;
    }

    @ProtoField(6)
    String getOwner() {
        return owner;
    }

    @ProtoField(value = 7, collectionImplementation = HashSet.class)
    Set<String> getScopes() {
        return scopes;
    }

    @ProtoField(8)
    String getServerId() {
        return serverId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof ResourceEvent that &&
                super.equals(that) &&
                Objects.equals(name, that.name) &&
                Objects.equals(owner, that.owner) &&
                Objects.equals(serverId, that.serverId) &&
                Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id, name, owner, serverId, type);
    }

    @Override
    public String toString() {
        return String.format("ResourceRemovedEvent [ id=%s, name=%s, eventType=%s]", id, name, eventType());
    }

    @Override
    public void addInvalidations(StoreFactoryCacheManager cache, Set<String> invalidations) {
        switch (eventType()) {
            case REMOVED -> cache.resourceRemoval(id, name, type, uris, owner, scopes, serverId, invalidations);
            case UPDATED -> cache.resourceUpdated(id, name, type, uris, scopes, serverId, owner, invalidations);
        }
    }

}
