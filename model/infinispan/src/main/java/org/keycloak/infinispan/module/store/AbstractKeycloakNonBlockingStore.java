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
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import jakarta.persistence.EntityManager;

import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.infinispan.module.cache.AbstractKeycloakStoreConfiguration;
import org.keycloak.infinispan.module.configuration.global.KeycloakConfiguration;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import org.infinispan.commons.util.concurrent.CompletableFutures;
import org.infinispan.persistence.spi.InitializationContext;
import org.infinispan.persistence.spi.MarshallableEntry;
import org.infinispan.persistence.spi.MarshallableEntryFactory;
import org.infinispan.persistence.spi.NonBlockingStore;
import org.infinispan.persistence.spi.PersistenceException;
import org.infinispan.util.concurrent.BlockingManager;


abstract class AbstractKeycloakNonBlockingStore<K, V> implements NonBlockingStore<K, V> {

    protected KeycloakSessionFactory keycloakSessionFactory;
    protected BlockingManager blockingManager;
    protected MarshallableEntryFactory<K, V> entryFactory;
    protected boolean offline;

    @Override
    public CompletionStage<Void> start(InitializationContext ctx) {
        AbstractKeycloakStoreConfiguration<?> configuration = ctx.getConfiguration();
        if (!configuration.shared()) {
            CompletableFuture.failedFuture(new PersistenceException("Expects a shared store."));
        }
        offline = configuration.isOfflineSessions();
        keycloakSessionFactory = ctx.getGlobalConfiguration()
                .module(KeycloakConfiguration.class)
                .keycloakSessionFactory();
        blockingManager = ctx.getBlockingManager();
        entryFactory = ctx.getMarshallableEntryFactory();
        return CompletableFutures.completedNull();
    }

    @Override
    public CompletionStage<Void> stop() {
        return CompletableFutures.completedNull();
    }

    @Override
    public Set<Characteristic> characteristics() {
        return EnumSet.of(Characteristic.SHAREABLE);
    }

    @Override
    public final CompletionStage<MarshallableEntry<K, V>> load(int segment, Object key) {
        var realKey = canLoad(key);
        if (realKey == null) {
            return CompletableFutures.completedNull();
        }
        return executeBlocking(this::actualLoad, realKey, "jpa-load-" + realKey);
    }

    @Override
    public CompletionStage<Void> write(int segment, MarshallableEntry<? extends K, ? extends V> entry) {
        return executeBlocking(this::actualWrite, entry, "jpa-write-" + entry.getKey());
    }

    @Override
    public CompletionStage<Boolean> delete(int segment, Object key) {
        var realKey = canLoad(key);
        if (realKey == null) {
            return CompletableFutures.completedFalse();
        }
        return executeBlocking(this::actualDelete, realKey, "jpa-load-" + realKey);
    }

    protected abstract K canLoad(Object key);

    protected abstract MarshallableEntry<K, V> actualLoad(EntityManager entityManager, K key) throws IOException;

    protected abstract void actualWrite(EntityManager entityManager, MarshallableEntry<? extends K, ? extends V> entry) throws IOException;

    protected abstract boolean actualDelete(EntityManager entityManager, K key);

    protected KeycloakSession createSession() {
        return keycloakSessionFactory.create();
    }

    protected static EntityManager getEntityManager(KeycloakSession session) {
        return session.getProvider(JpaConnectionProvider.class).getEntityManager();
    }

    private <I, O> CompletionStage<O> executeBlocking(EntityManagerActionWithResult<I, O> action, I input, String traceId) {
        return blockingManager.supplyBlocking(() -> {
            try (var sessions = createSession()) {
                return action.execute(getEntityManager(sessions), input);
            } catch (IOException e) {
                throw new PersistenceException(e);
            }
        }, traceId);
    }

    private <I> CompletionStage<Void> executeBlocking(EntityManagerAction<I> action, I input, String traceId) {
        return blockingManager.runBlocking(() -> {
            try (var sessions = createSession()) {
                action.execute(getEntityManager(sessions), input);
            } catch (IOException e) {
                throw new PersistenceException(e);
            }
        }, traceId);
    }

    @FunctionalInterface
    protected interface EntityManagerActionWithResult<I, O> {
        O execute(EntityManager em, I input) throws IOException;
    }

    @FunctionalInterface
    protected interface EntityManagerAction<I> {
        void execute(EntityManager em, I input) throws IOException;
    }
}
