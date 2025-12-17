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

package org.keycloak.infinispan.module.persistence;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.CacheStoreMode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.infinispan.module.configuration.cache.LoginFailuresStoreConfiguration;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.jpa.entities.PersistedLoginFailures;
import org.keycloak.models.sessions.infinispan.changes.SessionEntityWrapper;
import org.keycloak.models.sessions.infinispan.entities.LoginFailureEntity;
import org.keycloak.models.sessions.infinispan.entities.LoginFailureKey;
import org.keycloak.storage.configuration.ServerConfigStorageProvider;

import org.hibernate.ReadOnlyMode;
import org.infinispan.Cache;
import org.infinispan.commons.CacheConfigurationException;
import org.infinispan.commons.util.IntSet;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.persistence.manager.PersistenceManager;
import org.infinispan.persistence.spi.InitializationContext;
import org.infinispan.persistence.spi.MarshallableEntry;
import org.infinispan.persistence.spi.MarshalledValue;
import org.infinispan.persistence.spi.PersistenceException;

public class LoginFailuresStore extends BaseKeycloakStore<LoginFailureKey, SessionEntityWrapper<LoginFailureEntity>, LoginFailuresStoreConfiguration> {

    public static Optional<LoginFailuresStore> findStore(Cache<?, ?> cache) {
        return ComponentRegistry.componentOf(cache, PersistenceManager.class)
                .getStores(LoginFailuresStore.class)
                .stream()
                .findAny();
    }

    @Override
    CompletionStage<Void> actualStart(InitializationContext ctx) {
        return validateNumberOfSegments(ctx.getCache().getCacheConfiguration());
    }

    @Override
    public Set<Characteristic> characteristics() {
        return EnumSet.of(Characteristic.SHAREABLE, Characteristic.SEGMENTABLE, Characteristic.BULK_READ, Characteristic.EXPIRATION);
    }

    public void deleteRealm(KeycloakSession session, RealmModel realm) {
        var em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        em.createNamedQuery("loginFailureDeleteRealm")
                .setParameter("realmId", realm.getId())
                .executeUpdate();
    }

    @Override
    boolean deleteInSession(KeycloakSession session, LoginFailureKey key) {
        var em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        var rows = em.createNamedQuery("loginFailureDelete")
                .setParameter("realmId", key.realmId())
                .setParameter("userId", key.userId())
                .executeUpdate();
        return rows > 0;
    }

    @Override
    void clearInSession(KeycloakSession session) {
        var em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        em.createNamedQuery("loginFailureClear").executeUpdate();
    }

    @Override
    void storeInSession(KeycloakSession session, int segment, MarshallableEntry<? extends LoginFailureKey, ? extends SessionEntityWrapper<LoginFailureEntity>> entry) {
        try {
            var em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
            var entity = new PersistedLoginFailures();
            var key = entry.getKey();
            entity.setRealmId(key.realmId());
            entity.setUserId(key.userId());
            entity.setSegment(segment);
            entity.setData(marshaller.objectToByteBuffer(entry.getMarshalledValue()));
            entity.setCreatedOn(entry.getMarshalledValue().getCreated());
            em.merge(entity);
        } catch (InterruptedException | IOException e) {
            throw new PersistenceException("Unable to store " + entry.getKey(), e);
        }
    }

    @Override
    MarshallableEntry<LoginFailureKey, SessionEntityWrapper<LoginFailureEntity>> loadInSession(KeycloakSession session, LoginFailureKey key) {
        try {
            var em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
            var primaryKey = new PersistedLoginFailures.Key(key.realmId(), key.userId());
            var entity = em.find(PersistedLoginFailures.class, primaryKey, ReadOnlyMode.READ_ONLY, CacheStoreMode.BYPASS);
            if (entity == null) {
                return null;
            }
            return createFrom(session, key, entity);
        } catch (IOException | ClassNotFoundException e) {
            throw new PersistenceException("Unable to load " + key, e);
        }
    }

    @Override
    protected TypedQuery<Number> createSizeQuery(EntityManager em, IntSet segments) {
        return em.createNamedQuery("loginFailureSegmentSize", Number.class)
                .setParameter("segments", segments);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected final PublisherQueryDefinition<PersistedLoginFailures, MarshallableEntry<LoginFailureKey, SessionEntityWrapper<LoginFailureEntity>>> entriesPublisherQuery() {
        return new PublisherQueryDefinition<>() {
            @Override
            public TypedQuery<PersistedLoginFailures> createQuery(EntityManager em, int segment) {
                return em.createNamedQuery("loginFailurePublishEntries", PersistedLoginFailures.class)
                        .setParameter("segment", segment);
            }

            @Override
            public MarshallableEntry<LoginFailureKey, SessionEntityWrapper<LoginFailureEntity>> map(KeycloakSession session, PersistedLoginFailures queryResult) {
                return mapFromQuery(session, queryResult);
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    protected final PublisherQueryDefinition<Object[], LoginFailureKey> keysPublisherQuery() {
        return new PublisherQueryDefinition<>() {
            @Override
            public TypedQuery<Object[]> createQuery(EntityManager em, int segment) {
                return em.createNamedQuery("loginFailurePublishKeys", Object[].class)
                        .setParameter("segment", segment);
            }

            @Override
            public LoginFailureKey map(KeycloakSession session, Object[] queryResult) {
                assert queryResult.length == 2;
                return new LoginFailureKey(String.valueOf(queryResult[0]), String.valueOf(queryResult[1]));
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    protected final PurgeExpiredQueryDefinition<PersistedLoginFailures, LoginFailureKey, SessionEntityWrapper<LoginFailureEntity>> purgeExpiredQueryDefinition() {
        return new PurgeExpiredQueryDefinition<>() {
            @Override
            public TypedQuery<PersistedLoginFailures> createQuery(KeycloakSession session, EntityManager em, String realmId, int segment, int currentTime) {
                var realm = session.realms().getRealm(realmId);
                var oldestCreatedOn = realm.isPermanentLockout() ?
                        currentTime :
                        currentTime - realm.getMaxDeltaTimeSeconds();
                return em.createNamedQuery("loginFailureExpiration", PersistedLoginFailures.class)
                        .setParameter("realmId", realmId)
                        .setParameter("segment", segment)
                        .setParameter("createdOn", oldestCreatedOn);
            }

            @Override
            public MarshallableEntry<LoginFailureKey, SessionEntityWrapper<LoginFailureEntity>> map(KeycloakSession session, PersistedLoginFailures queryResult) {
                return mapFromQuery(session, queryResult);
            }

            @Override
            public Query deleteQuery(KeycloakSession session, EntityManager em, String realmId, int segment, List<MarshallableEntry<LoginFailureKey, SessionEntityWrapper<LoginFailureEntity>>> results) {
                var users = results.stream()
                        .map(MarshallableEntry::getKey)
                        .map(LoginFailureKey::userId)
                        .toList();
                return em.createNamedQuery("loginFailureDeleteMulti")
                        .setParameter("segment", segment)
                        .setParameter("realmId", realmId)
                        .setParameter("userId", users);
            }
        };
    }

    private CompletionStage<Void> validateNumberOfSegments(Configuration configuration) {
        return withTransaction("login-failures-segment-verification", session -> {
            var segments = configuration.clustering().hash().numSegments();
            var provider = session.getProvider(ServerConfigStorageProvider.class);
            var storedSegments = provider.find("login-failures-segments");
            if (storedSegments.isEmpty()) {
                provider.store("login-failures-segments", Integer.toString(segments));
                return null;
            }
            if (segments != Integer.parseInt(storedSegments.get())) {
                throw new CacheConfigurationException("The configured segments do not match for login-failures-store. Stored:%s, configured:%d".formatted(storedSegments.get(), segments));
            }
            return null;
        });
    }

    private MarshallableEntry<LoginFailureKey, SessionEntityWrapper<LoginFailureEntity>> mapFromQuery(KeycloakSession session, PersistedLoginFailures entity) {
        try {
            var key = new LoginFailureKey(entity.getRealmId(), entity.getUserId());
            return createFrom(session, key, entity);
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private MarshallableEntry<LoginFailureKey, SessionEntityWrapper<LoginFailureEntity>> createFrom(KeycloakSession session, LoginFailureKey key, PersistedLoginFailures entity) throws IOException, ClassNotFoundException {
        RealmModel realm = session.realms().getRealm(key.realmId());
        MarshalledValue value = (MarshalledValue) marshaller.objectFromByteBuffer(entity.getData());
        assert entity.getCreatedOn() == value.getCreated();
        var entry = entryFactory.create(key, value.getValueBytes(), value.getMetadataBytes(), value.getInternalMetadataBytes(), value.getCreated(), value.getLastUsed());
        var updatedMetadata = entry.getMetadata().builder()
                .lifespan(realm.getMaxDeltaTimeSeconds(), TimeUnit.SECONDS)
                .build();
        return entryFactory.create(key, entry.getValue(), updatedMetadata, entry.getInternalMetadata(), entry.created(), entry.lastUsed());
    }
}
