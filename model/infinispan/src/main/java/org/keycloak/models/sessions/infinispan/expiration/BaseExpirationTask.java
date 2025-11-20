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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.models.KeycloakSession;

import org.infinispan.util.concurrent.BlockingManager;

abstract class BaseExpirationTask implements ExpirationTask {

    private final int intervalSeconds;
    private final BlockingManager blockingManager;
    private final AtomicReference<BlockingManager.ScheduledBlockingCompletableStage<Void>> future;
    private final Runnable safePurgeExpired;

    BaseExpirationTask(KeycloakSession session, int intervalSeconds) {
        this.future = new AtomicReference<>();
        this.intervalSeconds = intervalSeconds;
        var provider = session.getProvider(InfinispanConnectionProvider.class);
        this.blockingManager = provider.getBlockingManager();
        this.safePurgeExpired = () -> {
            try {
                purgeExpired();
            } catch (RuntimeException e) {
                //log
            }
        };
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

    abstract void purgeExpired();

    int expiration() {
        return intervalSeconds;
    }

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
        return blockingManager.scheduleRunBlocking(safePurgeExpired, intervalSeconds, TimeUnit.SECONDS, "session-purge-expired");
    }

    private void queueNextSchedule(CompletionStage<Void> future) {
        future.exceptionally(throwable -> null)
                .thenRun(this::schedule);
    }
}
