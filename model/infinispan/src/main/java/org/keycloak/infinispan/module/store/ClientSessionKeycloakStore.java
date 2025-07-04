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
import java.util.concurrent.ConcurrentHashMap;

import jakarta.persistence.EntityManager;

import org.keycloak.models.jpa.session.PersistentClientSessionEntity;
import org.keycloak.models.session.PersistentAuthenticatedClientSessionAdapter;
import org.keycloak.models.sessions.infinispan.entities.AuthenticatedClientSessionEntity;
import org.keycloak.models.sessions.infinispan.entities.EmbeddedClientSessionKey;
import org.keycloak.util.JsonSerialization;

import org.infinispan.persistence.spi.MarshallableEntry;

import static org.keycloak.models.jpa.session.JpaSessionUtil.offlineToString;

public class ClientSessionKeycloakStore extends AbstractKeycloakNonBlockingStore<EmbeddedClientSessionKey, AuthenticatedClientSessionEntity> {
    @Override
    protected EmbeddedClientSessionKey canLoad(Object key) {
        return key instanceof EmbeddedClientSessionKey eck ? eck : null;
    }

    @Override
    protected MarshallableEntry<EmbeddedClientSessionKey, AuthenticatedClientSessionEntity> actualLoad(EntityManager entityManager, EmbeddedClientSessionKey key) throws IOException {
        var dbKey = PersistentClientSessionEntity.keyFrom(key.userSessionId(), key.clientId(), offlineToString(offline));
        var dbEntity = entityManager.find(PersistentClientSessionEntity.class, dbKey);
        if (dbEntity == null) {
            return null;
        }

        var entity = new AuthenticatedClientSessionEntity();
        persistedToCached(entity, dbEntity, key);

        // TODO compute lifespan?
        return entryFactory.create(key, entity);
    }

    @Override
    protected void actualWrite(EntityManager entityManager, MarshallableEntry<? extends EmbeddedClientSessionKey, ? extends AuthenticatedClientSessionEntity> entry) throws IOException {
        var key = entry.getKey();
        var dbKey = PersistentClientSessionEntity.keyFrom(key.userSessionId(), key.clientId(), offlineToString(offline));
        var dbEntity = entityManager.find(PersistentClientSessionEntity.class, dbKey);
        if (dbEntity == null) {
            // new entry!
            dbEntity = new PersistentClientSessionEntity();
            cachedToPersisted(entry.getValue(), dbEntity, dbKey);
            entityManager.persist(dbEntity);
            return;
        }
        cachedToPersisted(entry.getValue(), dbEntity, dbKey);
    }

    @Override
    protected boolean actualDelete(EntityManager entityManager, EmbeddedClientSessionKey key) {
        var dbKey = PersistentClientSessionEntity.keyFrom(key.userSessionId(), key.clientId(), offlineToString(offline));
        var dbEntity = entityManager.find(PersistentClientSessionEntity.class, dbKey);
        if (dbEntity == null) {
            return false;
        }
        entityManager.remove(dbEntity);
        return true;
    }

    @Override
    public CompletionStage<Void> clear() {
        // TODO!
        return null;
    }

    private void cachedToPersisted(AuthenticatedClientSessionEntity cached, PersistentClientSessionEntity persisted, PersistentClientSessionEntity.Key key) throws IOException {
        var data = new PersistentAuthenticatedClientSessionAdapter.PersistentClientSessionData();
        data.setAction(cached.getAction());
        data.setAuthMethod(cached.getAuthMethod());
        data.setNotes(cached.getNotes());
        data.setRedirectUri(cached.getRedirectUri());
        persisted.setData(JsonSerialization.writeValueAsString(data));

        persisted.setUserSessionId(key.getUserSessionId());
        persisted.setClientId(key.getClientId());
        persisted.setClientStorageProvider(key.getClientStorageProvider());
        persisted.setExternalClientId(key.getExternalClientId());
        persisted.setOffline(key.getOffline());
        persisted.setTimestamp(cached.getTimestamp());
    }

    private void persistedToCached(AuthenticatedClientSessionEntity cached, PersistentClientSessionEntity persisted, EmbeddedClientSessionKey key) throws IOException {
        var data = JsonSerialization.readValue(persisted.getData(), PersistentAuthenticatedClientSessionAdapter.PersistentClientSessionData.class);
        cached.setAction(data.getAction());
        cached.setAuthMethod(data.getAuthMethod());
        cached.setNotes(data.getNotes() == null ? new ConcurrentHashMap<>() : data.getNotes());
        cached.setRedirectUri(data.getRedirectUri());

        cached.setOffline(offline);
        cached.setTimestamp(persisted.getTimestamp());
        cached.setUserSessionId(key.userSessionId());
        cached.setClientId(key.clientId());
        //cached.setRealmId(key.realmId());
    }
}
