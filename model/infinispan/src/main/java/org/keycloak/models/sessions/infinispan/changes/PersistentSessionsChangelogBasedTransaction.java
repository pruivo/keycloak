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

package org.keycloak.models.sessions.infinispan.changes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import org.infinispan.Cache;
import org.jboss.logging.Logger;
import org.keycloak.common.Profile;
import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.sessions.infinispan.SessionFunction;
import org.keycloak.models.sessions.infinispan.entities.SessionEntity;
import org.keycloak.models.sessions.infinispan.remotestore.RemoteCacheInvoker;

import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.CLIENT_SESSION_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.USER_SESSION_CACHE_NAME;

abstract public class PersistentSessionsChangelogBasedTransaction<K, V extends SessionEntity> extends AbstractKeycloakTransaction implements SessionsChangelogBasedTransaction<K, V> {

    private static final Logger LOG = Logger.getLogger(PersistentSessionsChangelogBasedTransaction.class);
    protected final KeycloakSession kcSession;
    protected final Map<K, SessionUpdatesList<V>> updates = new HashMap<>();
    private final List<SessionChangesPerformer<K, V>> changesPerformers;
    protected final Cache<K, SessionEntityWrapper<V>> cache;
    private final SessionFunction<V> lifespanMsLoader;
    private final SessionFunction<V> maxIdleTimeMsLoader;
    private final SessionFunction<V> offlineLifespanMsLoader;
    private final SessionFunction<V> offlineMaxIdleTimeMsLoader;

    public PersistentSessionsChangelogBasedTransaction(KeycloakSession session,
                                                       Cache<K, SessionEntityWrapper<V>> cache,
                                                       RemoteCacheInvoker remoteCacheInvoker,
                                                       SessionFunction<V> lifespanMsLoader,
                                                       SessionFunction<V> maxIdleTimeMsLoader,
                                                       SessionFunction<V> offlineLifespanMsLoader,
                                                       SessionFunction<V> offlineMaxIdleTimeMsLoader,
                                                       ArrayBlockingQueue<PersistentUpdate> batchingQueue,
                                                       SerializeExecutionsByKey<K> serializer) {
        kcSession = session;

        if (!Profile.isFeatureEnabled(Profile.Feature.PERSISTENT_USER_SESSIONS)) {
            throw new IllegalStateException("Persistent user sessions are not enabled");
        }

        if (! (
                cache.getName().equals(USER_SESSION_CACHE_NAME)
                        || cache.getName().equals(CLIENT_SESSION_CACHE_NAME)
        )) {
            throw new IllegalStateException("Cache name is not valid for persistent user sessions: " + cache.getName());
        }

        changesPerformers = List.of(
                new JpaChangesPerformer<>(cache.getName(), batchingQueue),
                new EmbeddedCachesChangesPerformer<>(cache, serializer),
                new RemoteCachesChangesPerformer<>(session, cache, remoteCacheInvoker)
        );

        this.cache = cache;
        this.lifespanMsLoader = lifespanMsLoader;
        this.maxIdleTimeMsLoader = maxIdleTimeMsLoader;
        this.offlineLifespanMsLoader = offlineLifespanMsLoader;
        this.offlineMaxIdleTimeMsLoader = offlineMaxIdleTimeMsLoader;
    }

    protected SessionFunction<V> getLifespanMsLoader(boolean offline) {
        if (offline) {
            return offlineLifespanMsLoader;
        } else {
            return lifespanMsLoader;
        }
    }

    protected SessionFunction<V> getMaxIdleMsLoader(boolean offline) {
        if (offline) {
            return offlineMaxIdleTimeMsLoader;
        } else {
            return maxIdleTimeMsLoader;
        }
    }

    public SessionEntityWrapper<V> get(K key, boolean offline){
        SessionUpdatesList<V> myUpdates = updates.get(key);
        if (myUpdates == null) {
            SessionEntityWrapper<V> wrappedEntity = cache.get(key);
            if (wrappedEntity == null) {
                return null;
            }
            wrappedEntity.getEntity().setOffline(offline);

            RealmModel realm = kcSession.realms().getRealm(wrappedEntity.getEntity().getRealmId());

            myUpdates = new SessionUpdatesList<>(realm, wrappedEntity);
            updates.put(key, myUpdates);

            return wrappedEntity;
        } else {
            // If entity is scheduled for remove, we don't return it.
            boolean scheduledForRemove = myUpdates.getUpdateTasks().stream()
                    .map(SessionUpdateTask::getOperation)
                    .anyMatch(SessionUpdateTask.CacheOperation.REMOVE::equals);

            return scheduledForRemove ? null : myUpdates.getEntityWrapper();
        }
    }

    @Override
    protected void commitImpl() {
        for (Map.Entry<K, SessionUpdatesList<V>> entry : updates.entrySet()) {
            SessionUpdatesList<V> sessionUpdates = entry.getValue();
            SessionEntityWrapper<V> sessionWrapper = sessionUpdates.getEntityWrapper();
            V entity = sessionWrapper.getEntity();
            boolean isOffline = entity.isOffline();

            // Don't save transient entities to infinispan. They are valid just for current transaction
            if (sessionUpdates.getPersistenceState() == UserSessionModel.SessionPersistenceState.TRANSIENT) continue;

            RealmModel realm = sessionUpdates.getRealm();

            long lifespanMs = getLifespanMsLoader(isOffline).apply(realm, sessionUpdates.getClient(), entity);
            long maxIdleTimeMs = getMaxIdleMsLoader(isOffline).apply(realm, sessionUpdates.getClient(), entity);

            MergedUpdate<V> merged = MergedUpdate.computeUpdate(sessionUpdates.getUpdateTasks(), sessionWrapper, lifespanMs, maxIdleTimeMs);

            if (merged != null) {
                changesPerformers.stream()
                        .filter(performer -> performer.shouldConsumeChange(entity))
                        .forEach(p -> p.registerChange(entry, merged));
            }
        }

        changesPerformers.forEach(SessionChangesPerformer::applyChanges);
    }

    @Override
    public void addTask(K key, SessionUpdateTask<V> originalTask) {
        if (! (originalTask instanceof PersistentSessionUpdateTask<V> task)) {
            throw new IllegalArgumentException("Task must be instance of PersistentSessionUpdateTask");
        }

        SessionUpdatesList<V> myUpdates = updates.get(key);
        if (myUpdates == null) {
            // Lookup entity from cache
            SessionEntityWrapper<V> wrappedEntity = cache.get(key);
            if (wrappedEntity == null) {
                LOG.tracef("Not present cache item for key %s", key);
                return;
            }
            // Cache does not contain the offline flag value so adding it
            wrappedEntity.getEntity().setOffline(task.isOffline());

            RealmModel realm = kcSession.realms().getRealm(wrappedEntity.getEntity().getRealmId());

            myUpdates = new SessionUpdatesList<>(realm, wrappedEntity);
            updates.put(key, myUpdates);
        }

        // Run the update now, so reader in same transaction can see it (TODO: Rollback may not work correctly. See if it's an issue..)
        task.runUpdate(myUpdates.getEntityWrapper().getEntity());
        myUpdates.add(task);
    }

    public void addTask(K key, SessionUpdateTask<V> task, V entity, UserSessionModel.SessionPersistenceState persistenceState) {
        if (entity == null) {
            throw new IllegalArgumentException("Null entity not allowed");
        }

        RealmModel realm = kcSession.realms().getRealm(entity.getRealmId());
        SessionEntityWrapper<V> wrappedEntity = new SessionEntityWrapper<>(entity);
        SessionUpdatesList<V> myUpdates = new SessionUpdatesList<>(realm, wrappedEntity, persistenceState);
        updates.put(key, myUpdates);

        if (task != null) {
            // Run the update now, so reader in same transaction can see it
            task.runUpdate(entity);
            myUpdates.add(task);
        }
    }

    public void reloadEntityInCurrentTransaction(RealmModel realm, K key, SessionEntityWrapper<V> entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Null entity not allowed");
        }

        SessionEntityWrapper<V> latestEntity = cache.get(key);
        if (latestEntity == null) {
            return;
        }

        SessionUpdatesList<V> newUpdates = new SessionUpdatesList<>(realm, latestEntity);

        SessionUpdatesList<V> existingUpdates = updates.get(key);
        if (existingUpdates != null) {
            newUpdates.setUpdateTasks(existingUpdates.getUpdateTasks());
        }

        updates.put(key, newUpdates);
    }

    @Override
    protected void rollbackImpl() {

    }

}
