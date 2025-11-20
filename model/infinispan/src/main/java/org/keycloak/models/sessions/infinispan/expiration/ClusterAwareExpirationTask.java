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

import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.session.UserSessionPersisterProvider;
import org.keycloak.models.utils.KeycloakModelUtils;

import org.infinispan.distribution.DistributionManager;

import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.WORK_CACHE_NAME;

class ClusterAwareExpirationTask extends BaseExpirationTask {

    private final KeycloakSessionFactory factory;
    private final DistributionManager distributionManager;

    ClusterAwareExpirationTask(KeycloakSession session, int intervalSeconds) {
        super(session, intervalSeconds);
        this.factory = session.getKeycloakSessionFactory();
        // the cache is not important, just needs to be clustered and have String key type.
        var cache = session.getProvider(InfinispanConnectionProvider.class).getCache(WORK_CACHE_NAME);
        if (!cache.getCacheConfiguration().clustering().cacheMode().isClustered()) {
            throw new IllegalStateException();
        }
        distributionManager = cache.getAdvancedCache().getDistributionManager();
    }

    @Override
    void purgeExpired() {
        KeycloakModelUtils.runJobInTransaction(factory, session -> {
            var provider = session.getProvider(UserSessionPersisterProvider.class);
            if (provider == null) {
                return;
            }
            session.realms().getRealmsStream()
                    .filter(this::isRealmAssignedToLocal)
                    .forEach(provider::removeExpired);
        });
    }

    private boolean isRealmAssignedToLocal(RealmModel realm) {
        return distributionManager.getCacheTopology().getDistribution(realm.getId()).isPrimary();
    }
}
