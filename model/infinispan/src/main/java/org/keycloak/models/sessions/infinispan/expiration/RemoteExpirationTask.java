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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.session.UserSessionPersisterProvider;
import org.keycloak.models.utils.KeycloakModelUtils;

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

import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.WORK_CACHE_NAME;

@ClientListener(includeCurrentState = true)
class RemoteExpirationTask extends BaseExpirationTask {

    private static final String MEMBER_KEY_PREFIX = "node:";
    private final KeycloakSessionFactory factory;
    private final RemoteCache<String, String> workCache;
    private final String nodeUUID = MEMBER_KEY_PREFIX + UUID.randomUUID();
    private final String nodeName;
    private final Set<String> membership = ConcurrentHashMap.newKeySet();

    RemoteExpirationTask(KeycloakSession session, int intervalSeconds) {
        super(session, intervalSeconds);
        this.factory = session.getKeycloakSessionFactory();
        this.workCache = session.getProvider(InfinispanConnectionProvider.class).getRemoteCache(WORK_CACHE_NAME);
        this.nodeName = session.getProvider(InfinispanConnectionProvider.class).getNodeInfo().nodeName();
    }

    @Override
    public void start() {
        workCache.addClientListener(this);
        sendHeartBeat();
        super.start();
    }

    @Override
    public void stop() {
        workCache.removeClientListener(this);
        super.stop();
        workCache.removeAsync(nodeUUID);
    }

    @Override
    void purgeExpired() {
        sendHeartBeat();
        try {
            KeycloakModelUtils.runJobInTransaction(factory, session -> {
                var provider = session.getProvider(UserSessionPersisterProvider.class);
                if (provider == null) {
                    return;
                }
                HashingPredicate predicate = new HashingPredicate(membership.stream().sorted().toList(), nodeUUID);
                session.realms().getRealmsStream()
                        .filter(predicate)
                        .forEach(provider::removeExpired);
            });
        } finally {
            sendHeartBeat();
        }
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
        workCache.putAsync(nodeUUID, nodeName, expiration() + 30, TimeUnit.SECONDS);
    }

    private record HashingPredicate(List<String> members, String myUUID) implements Predicate<RealmModel> {

        @Override
        public boolean test(RealmModel realmModel) {
            var index = MurmurHash3.getInstance().hash(realmModel.getId()) & members.size();
            return myUUID.equals(members.get(index));
        }
    }

}
