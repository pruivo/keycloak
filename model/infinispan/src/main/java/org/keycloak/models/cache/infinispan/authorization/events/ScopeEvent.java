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
@ProtoTypeId(Marshalling.SCOPE_EVENT)
public class ScopeEvent extends TypedInvalidationEvent implements AuthorizationCacheInvalidationEvent {

    private final String id;
    private final String name;
    private final String serverId;

    @ProtoFactory
    ScopeEvent(String id, Type eventType, String name, String serverId) {
        super(eventType);
        this.id = id;
        this.name = name;
        this.serverId = serverId;
    }

    public static ScopeEvent removed(String id, String name, String serverId) {
        return new ScopeEvent(id, Type.REMOVED, name, serverId);
    }

    public static ScopeEvent updated(String id, String name, String serverId) {
        return new ScopeEvent(id, Type.UPDATED, name, serverId);
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
    String getServerId() {
        return serverId;
    }

    @Override
    public String toString() {
        return String.format("ScopeRemovedEvent [ id=%s, name=%s, type=%s ]", id, name, eventType());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof ScopeEvent that &&
                super.equals(that) &&
                Objects.equals(name, that.name) &&
                Objects.equals(serverId, that.serverId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id, name, serverId);
    }

    @Override
    public void addInvalidations(StoreFactoryCacheManager cache, Set<String> invalidations) {
        switch (eventType()) {
            case REMOVED -> cache.scopeRemoval(id, name, serverId, invalidations);
            case UPDATED -> cache.scopeUpdated(id, name, serverId, invalidations);
        }

    }

}
