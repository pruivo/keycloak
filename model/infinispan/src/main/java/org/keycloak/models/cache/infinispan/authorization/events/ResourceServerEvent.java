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
import org.infinispan.protostream.annotations.ProtoTypeId;
import org.keycloak.marshalling.Marshalling;
import org.keycloak.models.cache.infinispan.authorization.StoreFactoryCacheManager;
import org.keycloak.models.cache.infinispan.events.TypedInvalidationEvent;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@ProtoTypeId(Marshalling.RESOURCE_SERVER_EVENT)
public class ResourceServerEvent extends TypedInvalidationEvent implements AuthorizationCacheInvalidationEvent {

    private final String id;

    @ProtoFactory
    ResourceServerEvent(String id, Type eventType) {
        super(eventType);
        this.id = Objects.requireNonNull(id);
    }

    public static ResourceServerEvent removed(String id) {
        return new ResourceServerEvent(id, Type.REMOVED);
    }

    public static ResourceServerEvent updated(String id) {
        return new ResourceServerEvent(id, Type.UPDATED);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof ResourceServerEvent that &&
                super.equals(that) &&
                Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id);
    }

    @Override
    public String toString() {
        return String.format("ResourceServerEvent [ id=%s, type=%s ]", id, eventType());
    }

    @Override
    public void addInvalidations(StoreFactoryCacheManager cache, Set<String> invalidations) {
        switch (eventType()) {
            case REMOVED -> cache.resourceServerRemoval(id, invalidations);
            case UPDATED -> cache.resourceServerUpdated(id, invalidations);
        }

    }

}
