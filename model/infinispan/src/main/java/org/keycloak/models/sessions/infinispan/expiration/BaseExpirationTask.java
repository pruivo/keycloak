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

package org.keycloak.models.sessions.infinispan.expiration;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.session.UserSessionPersisterProvider;
import org.keycloak.models.utils.KeycloakModelUtils;

import org.infinispan.util.concurrent.BlockingManager;

abstract class BaseExpirationTask implements ExpirationTask {

    private final AtomicReference<BlockingManager.ScheduledBlockingCompletableStage<Void>> future = new AtomicReference<>();
    private final KeycloakSessionFactory factory;
    private final int intervalSeconds;
    private final BlockingManager blockingManager;
    private final LongConsumer onTaskExecuted;

    BaseExpirationTask(KeycloakSessionFactory factory, BlockingManager blockingManager, int intervalSeconds, LongConsumer onTaskExecuted) {
        this.factory = Objects.requireNonNull(factory);
        this.intervalSeconds = intervalSeconds;
        this.blockingManager = Objects.requireNonNull(blockingManager);
        this.onTaskExecuted = Objects.requireNonNullElse(onTaskExecuted, value -> {});
    }

    @Override
    public void start() {
        var newSchedule = createSchedule();
        if (future.compareAndSet(null, newSchedule)) {
            queueNextSchedule(newSchedule);
        }
    }

    @Override
    public void stop() {
        var existing = future.getAndSet(null);
        if (existing == null) {
            return;
        }
        existing.cancel(true);
    }

    void purgeExpired() {
        long start = System.nanoTime();
        try {
            KeycloakModelUtils.runJobInTransaction(factory, session -> {
                var provider = session.getProvider(UserSessionPersisterProvider.class);
                if (provider == null) {
                    return;
                }

                session.realms().getRealmsStream()
                        .filter(realmFilter())
                        .forEach(provider::removeExpired);
            });
        } catch (RuntimeException e) {
            // TODO log
        } finally {
            onTaskExecuted.accept(System.nanoTime() - start);
        }
    }

    final int expiration() {
        return intervalSeconds;
    }

    abstract Predicate<RealmModel> realmFilter();

    private void schedule() {
        var existing = future.get();
        var newSchedule = createSchedule();
        if (future.compareAndSet(existing, newSchedule)) {
            queueNextSchedule(newSchedule);
            return;
        }
        newSchedule.cancel(true);
    }

    private BlockingManager.ScheduledBlockingCompletableStage<Void> createSchedule() {
        return blockingManager.scheduleRunBlocking(this::purgeExpired, intervalSeconds, TimeUnit.SECONDS, "session-purge-expired");
    }

    private void queueNextSchedule(CompletionStage<Void> future) {
        future.exceptionally(throwable -> null)
                .thenRun(this::schedule);
    }
}
