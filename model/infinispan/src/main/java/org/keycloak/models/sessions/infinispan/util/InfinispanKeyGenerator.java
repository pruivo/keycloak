/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.models.sessions.infinispan.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.infinispan.Cache;
import org.infinispan.affinity.KeyAffinityService;
import org.infinispan.affinity.KeyAffinityServiceFactory;
import org.infinispan.affinity.KeyGenerator;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.remoting.transport.Address;
import org.infinispan.util.concurrent.BlockingManager;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.sessions.infinispan.entities.SessionKey;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.sessions.StickySessionEncoderProvider;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class InfinispanKeyGenerator {

    private static final Logger log = Logger.getLogger(InfinispanKeyGenerator.class);

    private static final KeyGenerator<SessionKey> ONLINE_GENERATOR = SessionKey::randomOnlineSessionKey;
    private static final KeyGenerator<SessionKey> OFFLINE_GENERATOR = SessionKey::randomOfflineSessionKey;
    private static final KeyGenerator<String> STRING_GENERATOR = KeycloakModelUtils::generateId;

    private final Map<String, KeyAffinityService<String>> keyAffinityServices = new ConcurrentHashMap<>();
    private final Map<String, KeyAffinityServiceHolder> sessionsKeyAffinityServices = new ConcurrentHashMap<>();


    public String generateKeyString(KeycloakSession session, Cache<String, ?> cache) {
        // "wantsLocalKey" is true if route is not attached to the sticky session cookie. Without attached route, We want the key, which will be "owned" by this node.
        // This is needed due the fact that external loadbalancer will attach route corresponding to our node, which will be the owner of the particular key, hence we
        // will be able to lookup key locally.
        if (shouldAttachRoute(session) || isLocalMode(cache)) {
            return KeycloakModelUtils.generateId();
        }
        return keyAffinityServices.computeIfAbsent(cache.getName(), s -> createStringKeyAffinityService(cache))
                .getKeyForAddress(localAddress(cache));
    }

    public SessionKey generateSessionKey(KeycloakSession session, Cache<SessionKey, ?> cache, boolean offline) {
        // "wantsLocalKey" is true if route is not attached to the sticky session cookie. Without attached route, We want the key, which will be "owned" by this node.
        // This is needed due the fact that external loadbalancer will attach route corresponding to our node, which will be the owner of the particular key, hence we
        // will be able to lookup key locally.
        if (shouldAttachRoute(session) || isLocalMode(cache)) {
            return SessionKey.randomSessionKey(offline);
        }
        return sessionsKeyAffinityServices.computeIfAbsent(cache.getName(), cacheName -> createKeyAffinityHolder(cache))
                .service(offline)
                .getKeyForAddress(cache.getCacheManager().getAddress());
    }

    private static KeyAffinityServiceHolder createKeyAffinityHolder(Cache<SessionKey, ?> cache) {
        log.debugf("Registered key affinity service for cache '%s'", cache.getName());
        var executor = executor(cache);
        var online = KeyAffinityServiceFactory.newLocalKeyAffinityService(cache, ONLINE_GENERATOR, executor, 16);
        var offline = KeyAffinityServiceFactory.newLocalKeyAffinityService(cache, OFFLINE_GENERATOR, executor, 16);
        return new KeyAffinityServiceHolder(online, offline);
    }

    private static KeyAffinityService<String> createStringKeyAffinityService(Cache<String, ?> cache) {
        log.debugf("Registered key affinity service for cache '%s'", cache.getName());
        return KeyAffinityServiceFactory.newLocalKeyAffinityService(cache, STRING_GENERATOR, executor(cache), 16);
    }

    private static Executor executor(Cache<?, ?> cache) {
        return GlobalComponentRegistry.componentOf(cache.getCacheManager(), BlockingManager.class).asExecutor("key-affinity-" + cache.getName());
    }

    private static Address localAddress(Cache<?, ?> cache) {
        return cache.getCacheManager().getAddress();
    }

    private static boolean shouldAttachRoute(KeycloakSession session) {
        return session.getProvider(StickySessionEncoderProvider.class).shouldAttachRoute();
    }

    private static boolean isLocalMode(Cache<?, ?> cache) {
        return !cache.getCacheConfiguration().clustering().cacheMode().isClustered();
    }

    private record KeyAffinityServiceHolder(KeyAffinityService<SessionKey> onlineKeyAffinityService,
                                            KeyAffinityService<SessionKey> offlineKeyAffinityService) {

        KeyAffinityService<SessionKey> service(boolean offline) {
            return offline ? offlineKeyAffinityService : onlineKeyAffinityService;
        }

    }
}
