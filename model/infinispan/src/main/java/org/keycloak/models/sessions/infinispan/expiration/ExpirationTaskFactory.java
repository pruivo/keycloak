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

import org.infinispan.client.hotrod.RemoteCache;

import org.keycloak.Config;
import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.infinispan.util.InfinispanUtils;
import org.keycloak.models.KeycloakSession;

import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.WORK_CACHE_NAME;

public final class ExpirationTaskFactory {

    // 3 min
    private static final int DEFAULT_INTERVAL_SECONDS = 180;


    public static ExpirationTask create(KeycloakSession session) {
        return create(session, getUserSessionExpirationInterval());
    }

    public static ExpirationTask create(KeycloakSession session, int expirationIntervalSeconds) {
        var connectionProvider = session.getProvider(InfinispanConnectionProvider.class);
        var blockingManager = connectionProvider.getBlockingManager();

        if (InfinispanUtils.isEmbeddedInfinispan()) {
            var workCache = connectionProvider.getCache(WORK_CACHE_NAME);
            if (workCache.getCacheConfiguration().clustering().cacheMode().isClustered()) {
                var distributionManager = workCache.getAdvancedCache().getDistributionManager();
                return new DistributionAwareExpirationTask(session.getKeycloakSessionFactory(), blockingManager, expirationIntervalSeconds, distributionManager);
            }

            return new LocalExpirationTask(session.getKeycloakSessionFactory(), blockingManager, expirationIntervalSeconds);
        }

        RemoteCache<String, String> workCache = connectionProvider.getRemoteCache(WORK_CACHE_NAME);
        String nodeName = connectionProvider.getNodeInfo().nodeName();
        return new RemoteExpirationTask(session.getKeycloakSessionFactory(), blockingManager, expirationIntervalSeconds, workCache, nodeName);
    }

    public static int getUserSessionExpirationInterval() {
        // TODO where to put this!? "scheduled" is not document anywhere, but it is where the cluster tasks interval is configured.
        return Config.scope("scheduled").getInt("session-expiration-interval", DEFAULT_INTERVAL_SECONDS);
    }

}
