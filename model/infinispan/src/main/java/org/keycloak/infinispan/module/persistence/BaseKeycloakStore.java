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

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.keycloak.common.util.Time;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.infinispan.module.configuration.global.KeycloakConfiguration;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakSessionTaskWithResult;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;

import io.reactivex.rxjava3.core.Emitter;
import io.reactivex.rxjava3.core.Flowable;
import org.hibernate.jpa.AvailableHints;
import org.infinispan.commons.CacheConfigurationException;
import org.infinispan.commons.util.IntSet;
import org.infinispan.commons.util.IntSets;
import org.infinispan.commons.util.concurrent.CompletableFutures;
import org.infinispan.configuration.cache.AbstractStoreConfiguration;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.marshall.persistence.PersistenceMarshaller;
import org.infinispan.persistence.spi.InitializationContext;
import org.infinispan.persistence.spi.MarshallableEntry;
import org.infinispan.persistence.spi.MarshallableEntryFactory;
import org.infinispan.persistence.spi.NonBlockingStore;
import org.infinispan.util.concurrent.BlockingManager;
import org.jboss.logging.Logger;
import org.reactivestreams.Publisher;

abstract class BaseKeycloakStore<K, V, C extends AbstractStoreConfiguration<C>> implements NonBlockingStore<K, V> {

    private static final org.jboss.logging.Logger log = Logger.getLogger(MethodHandles.lookup().lookupClass());

    protected BlockingManager blockingManager;
    protected KeycloakSessionFactory keycloakSessionFactory;
    protected MarshallableEntryFactory<K, V> entryFactory;
    protected PersistenceMarshaller marshaller;
    protected C storeConfiguration;
    protected String cacheName;
    private DistributionManager distributionManager;
    private Configuration cacheConfiguration;

    @Override
    public final CompletionStage<Void> start(InitializationContext ctx) {
        keycloakSessionFactory = getKeycloakSessionFactory(ctx.getGlobalConfiguration());
        storeConfiguration = ctx.getConfiguration();
        blockingManager = ctx.getBlockingManager();
        entryFactory = ctx.getMarshallableEntryFactory();
        marshaller = ctx.getPersistenceMarshaller();
        cacheName = ctx.getCache().getName();
        distributionManager = ctx.getCache().getAdvancedCache().getDistributionManager();
        cacheConfiguration = ctx.getCache().getCacheConfiguration();
        validateStoreConfiguration();
        return actualStart(ctx);
    }


    @Override
    public final CompletionStage<Void> stop() {
        //nothing to do here.
        return CompletableFutures.completedNull();
    }

    @Override
    public final CompletionStage<MarshallableEntry<K, V>> load(int segment, Object key) {
        var keyStr = String.valueOf(key);
        var event = new CacheLoadEvent<MarshallableEntry<K, V>>(cacheName, keyStr);
        event.begin();
        //noinspection unchecked
        return withTransaction("%s-load-%s".formatted(cacheName, keyStr), session -> loadInSession(session, (K) key))
                .whenComplete(event);
    }

    @Override
    public final CompletionStage<Void> write(int segment, MarshallableEntry<? extends K, ? extends V> entry) {
        var keyStr = String.valueOf(entry.getKey());
        var event = new CacheStoreEvent(cacheName, keyStr);
        event.begin();
        return withTransaction("%s-store-%s".formatted(cacheName, keyStr), session -> {
            storeInSession(session, segment, entry);
            return (Void) null;
        }).whenComplete(event);
    }

    @Override
    public final CompletionStage<Boolean> delete(int segment, Object key) {
        var keyStr = String.valueOf(key);
        var event = new CacheDeleteEvent(cacheName, String.valueOf(key));
        event.begin();
        //noinspection unchecked
        return withTransaction("%s-delete-%s".formatted(cacheName, keyStr), session -> deleteInSession(session, (K) key))
                .whenComplete(event);
    }

    @Override
    public final CompletionStage<Void> clear() {
        var event = new CacheClearEvent(cacheName);
        event.begin();
        return withTransaction("%s-clear", session -> {
            clearInSession(session);
            return (Void) null;
        }).whenComplete(event);
    }

    // BULK_READ methods (optional)

    @Override
    public final CompletionStage<Long> size(IntSet segments) {
        return withTransaction("%s-size".formatted(cacheName), session -> {
            var em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
            var size = createSizeQuery(em, segments)
                    .setMaxResults(1)
                    .getSingleResult();
            return size.longValue();
        });
    }

    protected TypedQuery<Number> createSizeQuery(EntityManager em, IntSet segments) {
        throw new UnsupportedOperationException("Store characteristic included " + Characteristic.BULK_READ + ", but it does not implement size");
    }

    @Override
    public final Publisher<MarshallableEntry<K, V>> publishEntries(IntSet segments, Predicate<? super K> filter, boolean includeValues) {
        var queryDefinition = entriesPublisherQuery();
        var pub = Flowable.fromIterable(checkSegments(segments))
                .flatMap(segment -> Flowable.<Stream<MarshallableEntry<K, V>>, Integer>generate(() -> 0, // offset starts at zero.
                        (offset, emitter) -> {
                            return publishWithPagination(offset,
                                    segment,
                                    e -> filter.test(e.getKey()),
                                    queryDefinition,
                                    emitter);
                        }))
                .flatMap(Flowable::fromStream);
        return blockingManager.blockingPublisher(pub);
    }

    protected <QT> PublisherQueryDefinition<QT, MarshallableEntry<K, V>> entriesPublisherQuery() {
        throw new UnsupportedOperationException("Store characteristic included " + Characteristic.BULK_READ + ", but it does not implement entryPublisher");
    }

    @Override
    public final Publisher<K> publishKeys(IntSet segments, Predicate<? super K> filter) {
        var queryDefinition = keysPublisherQuery();
        var pub = Flowable.fromIterable(checkSegments(segments))
                .flatMap(segment -> Flowable.<Stream<K>, Integer>generate(() -> 0, // offset starts at zero.
                        (offset, emitter) -> {
                            return publishWithPagination(offset,
                                    segment,
                                    filter,
                                    queryDefinition,
                                    emitter);
                        }))
                .flatMap(Flowable::fromStream);
        return blockingManager.blockingPublisher(pub);
    }

    protected <QT> PublisherQueryDefinition<QT, K> keysPublisherQuery() {
        throw new UnsupportedOperationException("Store characteristic included " + Characteristic.BULK_READ + ", but it does not implement keyPublisher");
    }

    // EXPIRATION methods (optional)


    @Override
    public final Publisher<MarshallableEntry<K, V>> purgeExpired() {
        var queryDefinition = purgeExpiredQueryDefinition();
        var realms = KeycloakModelUtils.runJobInTransactionWithResult(keycloakSessionFactory, session -> session.realms().getRealmsStream().map(RealmModel::getId).toList());
        var currentTime = Time.currentTime();
        var segments = distributionManager == null ?
                allSegments() :
                distributionManager.getCacheTopology().getLocalPrimarySegments();
        var pub = Flowable.fromIterable(realms)
                .flatMap(realmId -> Flowable.fromIterable(segments)
                        .flatMap(segment -> purgeExpired(realmId, segment, currentTime, queryDefinition)));
        return blockingManager.blockingPublisher(pub);
    }

    private <QT> Publisher<MarshallableEntry<K, V>> purgeExpired(String realmId, int segment, int currentTime, PurgeExpiredQueryDefinition<QT, K, V> queryDefinition) {
        return Flowable.<List<MarshallableEntry<K, V>>>generate(emitter -> {
            try {
                KeycloakModelUtils.runJobInTransaction(keycloakSessionFactory, session -> purgeExpiredInTransaction(session, realmId, segment, currentTime, queryDefinition, emitter));
            } catch (Throwable throwable) {
                emitter.onError(throwable);
            }
        }).flatMap(Flowable::fromIterable);
    }

    private <QT> void purgeExpiredInTransaction(KeycloakSession session, String realmId, int segment, int currentTime, PurgeExpiredQueryDefinition<QT, K, V> queryDefinition, Emitter<List<MarshallableEntry<K, V>>> emitter) {
        var batchSize = storeConfiguration.maxBatchSize();
        EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();

        var results = queryDefinition.createQuery(session, em, realmId, segment, currentTime)
                .setHint(AvailableHints.HINT_READ_ONLY, true)
                .setMaxResults(batchSize)
                .getResultStream()
                .map(entity -> queryDefinition.map(session, entity))
                .toList();
        var count = queryDefinition.deleteQuery(session, em, realmId, segment, results)
                        .executeUpdate();
        log.debugf("%s-store: removed %d expired entries.", cacheName, count);
        emitter.onNext(results);
        if (results.size() < batchSize) {
            emitter.onComplete();
        }
    }

    protected <QT> PurgeExpiredQueryDefinition<QT, K, V> purgeExpiredQueryDefinition() {
        throw new UnsupportedOperationException("Store characteristic included " + Characteristic.EXPIRATION + ", but it does not implement purgeExpired");
    }

    abstract CompletionStage<Void> actualStart(InitializationContext ctx);

    abstract MarshallableEntry<K, V> loadInSession(KeycloakSession session, K key);

    abstract void storeInSession(KeycloakSession session, int segment, MarshallableEntry<? extends K, ? extends V> entry);

    abstract boolean deleteInSession(KeycloakSession session, K key);

    abstract void clearInSession(KeycloakSession session);

    protected <T> CompletionStage<T> withTransaction(String taskName, KeycloakSessionTaskWithResult<T> task) {
        return blockingManager.supplyBlocking(() -> KeycloakModelUtils.runJobInTransactionWithResult(keycloakSessionFactory, null, task, taskName), taskName);
    }

    private <QT, PT> int publishWithPagination(int offset, int segment, Predicate<? super PT> filter, PublisherQueryDefinition<QT, PT> queryDefinition, Emitter<Stream<PT>> emitter) {
        try {
            return KeycloakModelUtils.runJobInTransactionWithResult(keycloakSessionFactory, session -> {
                var batchSize = storeConfiguration.maxBatchSize();
                var em = session.getProvider(JpaConnectionProvider.class).getEntityManager();

                var results = queryDefinition.createQuery(em, segment)
                        .setHint(AvailableHints.HINT_READ_ONLY, true)
                        .setFirstResult(offset)
                        .setMaxResults(batchSize)
                        .getResultStream()
                        .map(qt -> queryDefinition.map(session, qt))
                        .toList();

                emitter.onNext(results.stream().filter(filter));

                int nextOffset = offset + batchSize;
                if (results.size() < batchSize) {
                    // last batch, signal completion.
                    emitter.onComplete();
                }
                return nextOffset;
            });
        } catch (Throwable throwable) {
            emitter.onError(throwable);
            return offset;
        }
    }

    private IntSet checkSegments(IntSet inputSegments) {
        return Objects.requireNonNullElseGet(inputSegments, this::allSegments);
    }

    private IntSet allSegments() {
        return IntSets.immutableRangeSet(cacheConfiguration.clustering().hash().numSegments());
    }

    private void validateStoreConfiguration() {
        if (!storeConfiguration.shared()) {
            throw new CacheConfigurationException("The %s store must be shared.".formatted(cacheName));
        }
        if (!storeConfiguration.segmented()) {
            throw new CacheConfigurationException("The %s store must be segmented.".formatted(cacheName));
        }
    }

    private static KeycloakSessionFactory getKeycloakSessionFactory(GlobalConfiguration configuration) {
        return Optional.ofNullable(configuration.module(KeycloakConfiguration.class))
                .map(KeycloakConfiguration::keycloakSessionFactory)
                .orElseThrow(() -> new CacheConfigurationException("KeycloakSessionFactory not found"));
    }

}
