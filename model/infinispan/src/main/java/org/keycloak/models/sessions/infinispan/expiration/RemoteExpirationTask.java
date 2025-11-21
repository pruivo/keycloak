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

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.annotation.ClientListener;
import org.infinispan.client.hotrod.event.ClientCacheEntryCreatedEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryExpiredEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryRemovedEvent;
import org.infinispan.commons.hash.MurmurHash3;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryExpired;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.util.concurrent.BlockingManager;

@ClientListener(includeCurrentState = true)
class RemoteExpirationTask extends BaseExpirationTask {

    private static final String MEMBER_KEY_PREFIX = "node:";
    private static final int LIFESPAN_INCREASE_SECONDS = 30;

    private final RemoteCache<String, String> workCache;
    private final String nodeUUID = MEMBER_KEY_PREFIX + UUID.randomUUID();
    private final String nodeName;
    private final Set<String> membership = ConcurrentHashMap.newKeySet();

    RemoteExpirationTask(KeycloakSessionFactory factory, BlockingManager blockingManager, int intervalSeconds, LongConsumer onTaskExecuted, RemoteCache<String, String> workCache, String nodeName) {
        super(factory, blockingManager, intervalSeconds, onTaskExecuted);
        this.workCache = Objects.requireNonNull(workCache);
        this.nodeName = Objects.requireNonNull(nodeName);
    }

    @Override
    public final void start() {
        workCache.addClientListener(this);
        sendHeartBeat();
        super.start();
    }

    @Override
    public final void stop() {
        super.stop();
        // do not block the shutdown, if it is received, good, if not, it will expire.
        workCache.removeAsync(nodeUUID);
        workCache.removeClientListener(this);
    }

    @Override
    final void purgeExpired() {
        sendHeartBeat();
        try {
            super.purgeExpired();
        } finally {
            sendHeartBeat();
        }
    }

    @Override
    final Predicate<RealmModel> realmFilter() {
        return new HashingPredicate(membership.stream().sorted().toList(), nodeUUID);
    }

    @CacheEntryCreated
    public void onKeycloakConnected(ClientCacheEntryCreatedEvent<String> event) {
        addKeycloakNode(event.getKey());
    }

    @CacheEntryModified
    public void onHeartbeat(ClientCacheEntryCreatedEvent<String> event) {
        addKeycloakNode(event.getKey());
    }

    @CacheEntryExpired
    public void onMissingHeartbeat(ClientCacheEntryExpiredEvent<String> event) {
        removeKeycloakNode(event.getKey());
    }

    @CacheEntryRemoved
    public void onKeycloakDisconnect(ClientCacheEntryRemovedEvent<String> event) {
        removeKeycloakNode(event.getKey());
    }

    private void addKeycloakNode(String uuid) {
        if (uuid.startsWith(MEMBER_KEY_PREFIX)) {
            membership.add(uuid);
        }
    }

    private void removeKeycloakNode(String uuid) {
        if (uuid.startsWith(MEMBER_KEY_PREFIX)) {
            membership.remove(uuid);
        }
    }

    private void sendHeartBeat() {
        // we don't care about it, we sent it frequently.
        workCache.putAsync(nodeUUID, nodeName, expiration() + LIFESPAN_INCREASE_SECONDS, TimeUnit.SECONDS);
    }

    private record HashingPredicate(List<String> members, String myUUID) implements Predicate<RealmModel> {

        @Override
        public boolean test(RealmModel realm) {
            var index = MurmurHash3.getInstance().hash(realm.getId()) & members.size();
            return myUUID.equals(members.get(index));
        }
    }

}
