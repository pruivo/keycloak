/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

import org.infinispan.protostream.annotations.Proto;
import org.infinispan.protostream.annotations.ProtoField;

public abstract class TypedInvalidationEvent extends InvalidationEvent {

    private final Type eventType;

    protected TypedInvalidationEvent(Type eventType) {
        this.eventType = eventType;
    }

    @ProtoField(2)
    public Type eventType() {
        return eventType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof TypedInvalidationEvent that &&
                super.equals(o) &&
                eventType == that.eventType;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + eventType.hashCode();
        return result;
    }

    @Proto
    public enum Type {
        UPDATED, REMOVED, ADDED
    }

}
