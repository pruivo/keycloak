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

import org.infinispan.protostream.annotations.ProtoField;
import org.infinispan.protostream.annotations.ProtoTypeId;
import org.keycloak.models.cache.infinispan.UserCacheManager;
import org.keycloak.marshalling.Marshalling;

import java.util.Objects;
import java.util.Set;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@ProtoTypeId(Marshalling.USER_UPDATED_EVENT)
public class UserUpdatedEvent extends InvalidationEvent implements UserCacheInvalidationEvent {

    private String userId;
    private String username;
    private String email;
    private String realmId;

    public static UserUpdatedEvent create(String userId, String username, String email, String realmId) {
        UserUpdatedEvent event = new UserUpdatedEvent();
        event.userId = userId;
        event.username = username;
        event.email = email;
        event.realmId = realmId;
        return event;
    }

    @Override
    public String getId() {
        return userId;
    }

    void setId(String id) {
        this.userId = id;
    }

    @ProtoField(2)
    String getUsername() {
        return username;
    }

    void setUsername(String username) {
        this.username = username;
    }

    @ProtoField(3)
    String getEmail() {
        return email;
    }

    void setEmail(String email) {
        this.email = email;
    }

    @ProtoField(4)
    String getRealmId() {
        return realmId;
    }

    void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    @Override
    public String toString() {
        return String.format("UserUpdatedEvent [ userId=%s, username=%s, email=%s ]", userId, username, email);
    }

    @Override
    public void addInvalidations(UserCacheManager userCache, Set<String> invalidations) {
        userCache.userUpdatedInvalidations(userId, username, email, realmId, invalidations);
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
        UserUpdatedEvent that = (UserUpdatedEvent) o;
        return Objects.equals(userId, that.userId) && Objects.equals(username, that.username) && Objects.equals(email, that.email) && Objects.equals(realmId, that.realmId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userId, username, email, realmId);
    }

}
