/*
 * Copyright 2025 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.infinispan.module.store;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.keycloak.infinispan.module.cache.UserSessionKeycloakStoreConfiguration;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.jpa.session.PersistentClientSessionEntity;
import org.keycloak.models.jpa.session.PersistentUserSessionEntity;
import org.keycloak.models.session.PersistentUserSessionAdapter;
import org.keycloak.models.sessions.infinispan.entities.UserSessionEntity;
import org.keycloak.util.JsonSerialization;

import org.infinispan.commons.configuration.ConfiguredBy;
import org.infinispan.persistence.spi.MarshallableEntry;

import static org.keycloak.models.jpa.session.JpaSessionUtil.offlineFromString;
import static org.keycloak.models.jpa.session.JpaSessionUtil.offlineToString;

@ConfiguredBy(UserSessionKeycloakStoreConfiguration.class)
public class UserSessionKeycloakStore extends AbstractKeycloakNonBlockingStore<String, UserSessionEntity> {

    @Override
    public CompletionStage<Void> clear() {
        // TODO!
        return null;
    }

    @Override
    protected String canLoad(Object key) {
        return String.valueOf(key);
    }

    @Override
    protected MarshallableEntry<String, UserSessionEntity> actualLoad(EntityManager entityManager, String userSessionId) throws IOException {
        var userSession = entityManager.find(PersistentUserSessionEntity.class, new PersistentUserSessionEntity.Key(userSessionId, offlineToString(offline)), LockModeType.PESSIMISTIC_READ);
        if (userSession == null) {
            return null;
        }

        var cachedEntity = new UserSessionEntity(userSessionId);
        persistedToCached(cachedEntity, userSession);

        var clientSessionQuery = entityManager.createNamedQuery("findClientSessionsByUserSession", PersistentClientSessionEntity.class);
        clientSessionQuery.setParameter("userSessionId", userSessionId);
        clientSessionQuery.setParameter("offline", offlineToString(offline));

        var clients = cachedEntity.getClientSessions();
        try (var stream = clientSessionQuery.getResultStream()) {
            stream.map(PersistentClientSessionEntity::computeClientId)
                    .forEach(clients::add);
        }

        // TODO compute lifespan?
        return entryFactory.create(userSessionId, cachedEntity);
    }

    @Override
    protected void actualWrite(EntityManager entityManager, MarshallableEntry<? extends String, ? extends UserSessionEntity> entry) throws IOException {
        var userSession = entityManager.find(PersistentUserSessionEntity.class, new PersistentUserSessionEntity.Key(entry.getKey(), offlineToString(offline)), LockModeType.PESSIMISTIC_WRITE);

        if (userSession == null) {
            userSession = new PersistentUserSessionEntity();
            cachedToPersisted(entry.getValue(), userSession);
            entityManager.persist(userSession);
            return;
        }

        cachedToPersisted(entry.getValue(), userSession);
    }

    @Override
    protected boolean actualDelete(EntityManager entityManager, String userSessionId) {
        var sessionEntity = entityManager.find(PersistentUserSessionEntity.class, new PersistentUserSessionEntity.Key(userSessionId, offlineToString(offline)), LockModeType.PESSIMISTIC_WRITE);
        if (sessionEntity == null) {
            return false;
        }
        entityManager.remove(sessionEntity);
        return true;
    }

    private void cachedToPersisted(UserSessionEntity cached, PersistentUserSessionEntity persisted) throws IOException {
        var data = new PersistentUserSessionAdapter.PersistentUserSessionData();
        cachedToPersistedData(cached, data);
        persisted.setData(JsonSerialization.writeValueAsString(data));
        persisted.setUserSessionId(cached.getId());
        persisted.setRealmId(cached.getRealmId());
        persisted.setUserId(cached.getUser());
        persisted.setCreatedOn(cached.getStarted());
        persisted.setLastSessionRefresh(cached.getLastSessionRefresh());
        persisted.setOffline(offlineToString(offline));
        persisted.setBrokerSessionId(cached.getBrokerSessionId());
    }

    @SuppressWarnings("removal")
    private void cachedToPersistedData(UserSessionEntity cached, PersistentUserSessionAdapter.PersistentUserSessionData data) {
        data.setBrokerSessionId(cached.getBrokerSessionId());
        data.setBrokerUserId(cached.getBrokerUserId());
        data.setIpAddress(cached.getIpAddress());
        data.setAuthMethod(cached.getAuthMethod());
        data.setRememberMe(cached.isRememberMe());
        data.setStarted(cached.getStarted());
        data.setNotes(cached.getNotes());
        if (cached.getState() != null) {
            data.setState(cached.getState().toString());
        }
        data.setLoginUsername(cached.getLoginUsername());
    }

    private void persistedToCached(UserSessionEntity cached, PersistentUserSessionEntity persisted) throws IOException {
        var data = JsonSerialization.readValue(persisted.getData(), PersistentUserSessionAdapter.PersistentUserSessionData.class);
        persistedDataToCached(cached, data);
        cached.setRealmId(persisted.getRealmId());
        cached.setUser(persisted.getUserId());
        cached.setStarted(persisted.getCreatedOn());
        cached.setLastSessionRefresh(persisted.getLastSessionRefresh());
        cached.setOffline(offlineFromString(persisted.getOffline()));
        cached.setBrokerSessionId(persisted.getBrokerSessionId());
    }

    @SuppressWarnings("removal")
    private void persistedDataToCached(UserSessionEntity cached, PersistentUserSessionAdapter.PersistentUserSessionData data) {
        cached.setBrokerSessionId(data.getBrokerSessionId());
        cached.setBrokerUserId(data.getBrokerUserId());
        cached.setIpAddress(data.getIpAddress());
        cached.setAuthMethod(data.getAuthMethod());
        cached.setRememberMe(data.isRememberMe());
        cached.setStarted(data.getStarted());
        cached.setNotes(data.getNotes());
        if (data.getState() != null) {
            // Migration to Keycloak 3.2
            if (data.getState().equals("LOGGING_IN")) {
                cached.setState(UserSessionModel.State.LOGGED_IN);
            }

            cached.setState(UserSessionModel.State.valueOf(data.getState()));
        }
        cached.setLoginUsername(data.getLoginUsername());
    }
}
