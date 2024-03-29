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
import org.keycloak.models.FederatedIdentityModel;
import org.keycloak.models.cache.infinispan.UserCacheManager;
import org.keycloak.marshalling.Marshalling;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Used when user added/removed
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@ProtoTypeId(Marshalling.USER_FULL_INVALIDATION_EVENT)
public class UserFullInvalidationEvent extends InvalidationEvent implements UserCacheInvalidationEvent {

    private String userId;
    private String username;
    private String email;
    private String realmId;
    private boolean identityFederationEnabled;
    private Map<String, String> federatedIdentities;

    public static UserFullInvalidationEvent create(String userId, String username, String email, String realmId, boolean identityFederationEnabled, Stream<FederatedIdentityModel> federatedIdentities) {
        UserFullInvalidationEvent event = new UserFullInvalidationEvent();
        event.userId = userId;
        event.username = username;
        event.email = email;
        event.realmId = realmId;

        event.identityFederationEnabled = identityFederationEnabled;
        if (identityFederationEnabled) {
            event.federatedIdentities = federatedIdentities.collect(Collectors.toMap(FederatedIdentityModel::getIdentityProvider,
                    FederatedIdentityModel::getUserId));
        }

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

    @ProtoField(5)
    boolean isIdentityFederationEnabled() {
        return identityFederationEnabled;
    }

    void setIdentityFederationEnabled(boolean identityFederationEnabled) {
        this.identityFederationEnabled = identityFederationEnabled;
    }

    @ProtoField(value = 6, mapImplementation = HashMap.class)
    public Map<String, String> getFederatedIdentities() {
        return federatedIdentities;
    }

    void setFederatedIdentities(Map<String, String> federatedIdentities) {
        this.federatedIdentities = federatedIdentities;
    }

    @Override
    public String toString() {
        return String.format("UserFullInvalidationEvent [ userId=%s, username=%s, email=%s ]", userId, username, email);
    }

    @Override
    public void addInvalidations(UserCacheManager userCache, Set<String> invalidations) {
        userCache.fullUserInvalidation(userId, username, email, realmId, identityFederationEnabled, federatedIdentities, invalidations);
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
        UserFullInvalidationEvent that = (UserFullInvalidationEvent) o;
        return identityFederationEnabled == that.identityFederationEnabled && Objects.equals(userId, that.userId) && Objects.equals(username, that.username) && Objects.equals(email, that.email) && Objects.equals(realmId, that.realmId) && Objects.equals(federatedIdentities, that.federatedIdentities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userId, username, email, realmId, identityFederationEnabled, federatedIdentities);
    }

}
