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

package org.keycloak.models.sessions.infinispan.remotestore;

import org.keycloak.common.util.Time;
import org.keycloak.models.ClientModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.sessions.infinispan.entities.AuthenticatedClientSessionEntity;
import org.keycloak.models.sessions.infinispan.entities.LoginFailureEntity;
import org.keycloak.models.sessions.infinispan.entities.LoginFailureKey;
import org.keycloak.models.sessions.infinispan.entities.SessionEntity;
import org.keycloak.models.sessions.infinispan.entities.SessionKey;
import org.keycloak.models.sessions.infinispan.entities.UserSessionEntity;
import org.keycloak.models.sessions.infinispan.util.SessionTimeouts;

public interface Expiration<K, V extends SessionEntity> extends RemoteCacheInvoker.MaxIdleTimeLoader<K> {

    Expiration<LoginFailureKey, LoginFailureEntity> LOGIN_FAILURE_ENTITY_EXPIRATION = new Expiration<>() {
        @Override
        public long getMaxIdleTimeMs(LoginFailureKey key, RealmModel realm) {
            return Time.toMillis(realm.getMaxDeltaTimeSeconds());
        }

        @Override
        public long lifespan(LoginFailureKey key, LoginFailureEntity value, RealmModel realm, ClientModel client) {
            return SessionTimeouts.getLoginFailuresLifespanMs(realm, client, value);
        }

        @Override
        public long maxIdle(LoginFailureKey key, LoginFailureEntity value, RealmModel realm, ClientModel client) {
            return SessionTimeouts.getLoginFailuresMaxIdleMs(realm, client, value);
        }
    };

    Expiration<SessionKey, UserSessionEntity> USER_SESSION_ENTITY_EXPIRATION = new Expiration<>() {

        @Override
        public long getMaxIdleTimeMs(SessionKey key, RealmModel realm) {
            return key.offline() ?
                    realm.getOfflineSessionIdleTimeout() :
                    realm.getSsoSessionMaxLifespan(); // We won't write to the remoteCache during token refresh, so the timeout needs to be longer.
        }

        @Override
        public long lifespan(SessionKey key, UserSessionEntity value, RealmModel realm, ClientModel client) {
            return key.offline() ?
                    SessionTimeouts.getOfflineSessionLifespanMs(realm, client, value) :
                    SessionTimeouts.getUserSessionLifespanMs(realm, client, value);
        }

        @Override
        public long maxIdle(SessionKey key, UserSessionEntity value, RealmModel realm, ClientModel client) {
            return key.offline() ?
                    SessionTimeouts.getOfflineSessionMaxIdleMs(realm, client, value) :
                    SessionTimeouts.getUserSessionMaxIdleMs(realm, client, value);
        }
    };

    Expiration<SessionKey, AuthenticatedClientSessionEntity> AUTHENTICATED_CLIENT_SESSION_ENTITY_EXPIRATION = new Expiration<>() {

        @Override
        public long getMaxIdleTimeMs(SessionKey key, RealmModel realm) {
            return key.offline() ?
                    realm.getOfflineSessionIdleTimeout() :
                    realm.getSsoSessionMaxLifespan(); // We won't write to the remoteCache during token refresh, so the timeout needs to be longer.
        }

        @Override
        public long lifespan(SessionKey key, AuthenticatedClientSessionEntity value, RealmModel realm, ClientModel client) {
            return key.offline() ?
                    SessionTimeouts.getOfflineClientSessionLifespanMs(realm, client, value) :
                    SessionTimeouts.getClientSessionLifespanMs(realm, client, value);
        }

        @Override
        public long maxIdle(SessionKey key, AuthenticatedClientSessionEntity value, RealmModel realm, ClientModel client) {
            return key.offline() ?
                    SessionTimeouts.getOfflineClientSessionMaxIdleMs(realm, client, value) :
                    SessionTimeouts.getClientSessionMaxIdleMs(realm, client, value);
        }
    };

    long lifespan(K key, V value, RealmModel realm, ClientModel client);

    long maxIdle(K key, V value, RealmModel realm, ClientModel client);

}
