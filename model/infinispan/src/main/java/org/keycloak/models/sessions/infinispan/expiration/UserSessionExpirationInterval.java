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

import org.keycloak.Config;

public final class UserSessionExpirationInterval {

    // 3 min
    private static final int DEFAULT_INTERVAL_SECONDS = 180;

    private UserSessionExpirationInterval() {}

    public static int getUserSessionExpirationInterval() {
        // TODO where to put this!? "scheduled" is not document anywhere, but it is where the cluster tasks interval is configured.
        return Config.scope("scheduled").getInt("session-expiration-interval", DEFAULT_INTERVAL_SECONDS);
    }

}
