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
@ProtoTypeId(Marshalling.REALM_EVENT)
public class RealmEvent extends TypedInvalidationEvent implements RealmCacheInvalidationEvent {

    private final String realmId;
    private final String realmName;

    @ProtoFactory
    RealmEvent(String id, Type eventType, String realmName) {
        super(eventType);
        this.realmId = id;
        this.realmName = realmName;
    }

    public static RealmEvent removed(String realmId, String realmName) {
        return new RealmEvent(Objects.requireNonNull(realmId), Type.REMOVED, Objects.requireNonNull(realmName));
    }

    public static RealmEvent updated(String realmId, String realmName) {
        return new RealmEvent(Objects.requireNonNull(realmId), Type.UPDATED, Objects.requireNonNull(realmName));
    }

    @Override
    public String getId() {
        return realmId;
    }

    @ProtoField(3)
    String getRealmName() {
        return realmName;
    }

    @Override
    public String toString() {
        return String.format("RealmRemovedEvent [ realmId=%s, realmName=%s, type=%s ]", realmId, realmName, eventType());
    }

    @Override
    public void addInvalidations(RealmCacheManager realmCache, Set<String> invalidations) {
        switch (eventType()) {
            case REMOVED -> realmCache.realmRemoval(realmId, realmName, invalidations);
            case UPDATED -> realmCache.realmUpdated(realmId, realmName, invalidations);
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
        RealmEvent that = (RealmEvent) o;
        return Objects.equals(realmName, that.realmName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), realmName);
    }

}
