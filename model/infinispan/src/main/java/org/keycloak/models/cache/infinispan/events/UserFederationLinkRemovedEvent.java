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

import java.util.Objects;
import java.util.Set;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@ProtoTypeId(Marshalling.USER_FEDERATION_LINK_REMOVED_EVENT)
public class UserFederationLinkRemovedEvent extends InvalidationEvent implements UserCacheInvalidationEvent {

    private String userId;
    private String realmId;
    private String identityProviderId;
    private String socialUserId;

    public static UserFederationLinkRemovedEvent create(String userId, String realmId, FederatedIdentityModel socialLink) {
        UserFederationLinkRemovedEvent event = new UserFederationLinkRemovedEvent();
        event.userId = userId;
        event.realmId = realmId;
        if (socialLink != null) {
            event.identityProviderId = socialLink.getIdentityProvider();
            event.socialUserId = socialLink.getUserId();
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
    public String getRealmId() {
        return realmId;
    }

    void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    @ProtoField(3)
    public String getIdentityProviderId() {
        return identityProviderId;
    }

    void setIdentityProviderId(String identityProviderId) {
        this.identityProviderId = identityProviderId;
    }

    @ProtoField(4)
    public String getSocialUserId() {
        return socialUserId;
    }

    void setSocialUserId(String socialUserId) {
        this.socialUserId = socialUserId;
    }

    @Override
    public String toString() {
        return String.format("UserFederationLinkRemovedEvent [ userId=%s, identityProviderId=%s, socialUserId=%s ]", userId, identityProviderId, socialUserId);
    }

    @Override
    public void addInvalidations(UserCacheManager userCache, Set<String> invalidations) {
        userCache.federatedIdentityLinkRemovedInvalidation(userId, realmId, identityProviderId, socialUserId, invalidations);
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
        UserFederationLinkRemovedEvent that = (UserFederationLinkRemovedEvent) o;
        return Objects.equals(userId, that.userId) && Objects.equals(realmId, that.realmId) && Objects.equals(identityProviderId, that.identityProviderId) && Objects.equals(socialUserId, that.socialUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userId, realmId, identityProviderId, socialUserId);
    }

}
