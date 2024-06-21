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

package org.keycloak.models.sessions.infinispan.query;

import java.util.Map;

import org.keycloak.marshalling.Marshalling;
import org.keycloak.models.sessions.infinispan.entities.AuthenticatedClientSessionEntity;
import org.keycloak.models.sessions.infinispan.entities.LoginFailureEntity;
import org.keycloak.models.sessions.infinispan.entities.RootAuthenticationSessionEntity;
import org.keycloak.models.sessions.infinispan.entities.UserSessionEntity;

import static java.lang.String.format;

public final class QueryHelper {

    private QueryHelper() {
    }

    private static final String REALM_PARAMETER = "realmId";
    private static final String USER_PARAMETER = "userId";
    private static final String BROKER_USER_PARAMETER = "brokerUserId";
    private static final String BROKER_SESSION_PARAMETER = "brokerSessionId";

    // authentication session
    public static final String DELETE_AUTHENTICATION_SESSION_BY_REALM = format("DELETE FROM %s WHERE realmId = :%s", Marshalling.protoEntity(RootAuthenticationSessionEntity.class), REALM_PARAMETER);

    // login failure
    public static final String DELETE_LOGIN_FAILURE_BY_REALM = format("DELETE FROM %s WHERE realmId = :%s", Marshalling.protoEntity(LoginFailureEntity.class), REALM_PARAMETER);

    // user session
    public static final String USER_SESSIONS_BY_REALM = format("FROM %s WHERE realmId = :%s", Marshalling.protoEntity(UserSessionEntity.class), REALM_PARAMETER);
    public static final String USER_SESSIONS_BY_REALM_AND_USER = format("%s && user = :%s", USER_SESSIONS_BY_REALM, USER_PARAMETER);
    public static final String USER_SESSIONS_BY_REALM_AND_BROKER_USER = format("%s && brokerUserId = :%s", USER_SESSIONS_BY_REALM, BROKER_USER_PARAMETER);
    public static final String USER_SESSIONS_BY_REALM_AND_BROKER_SESSION = format("%s && brokerSessionId = :%s", USER_SESSIONS_BY_REALM, BROKER_SESSION_PARAMETER);
    public static final String DELETE_USER_SESSIONS_BY_REALM = format("DELETE FROM %s WHERE realmId = :%s", Marshalling.protoEntity(UserSessionEntity.class), REALM_PARAMETER);

    // client session
    public static final String DELETE_CLIENT_SESSIONS_BY_REALM = format("DELETE FROM %s WHERE realmId = :%s", Marshalling.protoEntity(AuthenticatedClientSessionEntity.class), REALM_PARAMETER);

    public static Map<String, Object> realmParameter(String realmId) {
        return Map.of(REALM_PARAMETER, realmId);
    }

    public static Map<String, Object> realmAndUserParameters(String realmId, String userId) {
        return Map.of(REALM_PARAMETER, realmId, USER_PARAMETER, userId);
    }

    public static Map<String, Object> realmAndBrokerUserParameters(String realmId, String brokerUserId) {
        return Map.of(REALM_PARAMETER, realmId, BROKER_USER_PARAMETER, brokerUserId);
    }

    public static Map<String, Object> realmAndBrokerSessionParameters(String realmId, String brokerSessionId) {
        return Map.of(REALM_PARAMETER, realmId, BROKER_SESSION_PARAMETER, brokerSessionId);
    }


}
