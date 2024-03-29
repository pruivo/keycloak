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

package org.keycloak.marshalling;

import org.infinispan.configuration.global.GlobalConfigurationBuilder;

/**
 * Ids of the protostream type.
 * <p>
 * Read careful the following warning to ensure compatibility when updating schemas.
 * <p>
 * WARNING! IDs lower or equal than 65535 are reserved for internal Inifinispan classes and cannot be used.
 * WARNING! ID defined in this class must be unique. If one type is removed, its ID must not be reused. You have been
 * warned! The ID identifies the message, and it is stored and used to save space.
 * WARNING! The field IDs cannot be reused as well for the same reason.
 * WARNING! Primitive types cannot be null in proto3 syntax (Integer, String). Take that in consideration.
 * <p>
 * Be Aware of the following default in Proto3 syntax!
 * For strings, the default value is the empty string.
 * For bytes, the default value is empty bytes.
 * For bools, the default value is false.
 * For numeric types, the default value is zero.
 * For enums, the default value is the first defined enum value, which must be 0.
 * For message fields, the field is not set. (null)
 * <p>
 * Docs: <a href="https://protobuf.dev/programming-guides/proto3/">Language Guide (proto 3)</a>
 */
public final class Marshalling {

    private Marshalling() {
    }

    // anything bellow or equal to this ID is reserved for Infinispan classes.
    private static final int INFINISPAN_MAX_RESERVED_ID = 65535;

    // Model
    // see org.keycloak.models.UserSessionModel.State
    public static final int USER_STATE_ENUM = INFINISPAN_MAX_RESERVED_ID + 1;
    // see org.keycloak.sessions.CommonClientSessionModel.ExecutionStatus
    public static final int CLIENT_SESSION_EXECUTION_STATUS = INFINISPAN_MAX_RESERVED_ID + 2;
    // see org.keycloak.component.ComponentModel.MultiMapEntry
    public static final int MULTIMAP_ENTRY = INFINISPAN_MAX_RESERVED_ID + 3;
    // see org.keycloak.storage.UserStorageProviderModel
    public static final int USER_STORAGE_PROVIDER_MODES = INFINISPAN_MAX_RESERVED_ID + 4;
    // see org.keycloak.storage.managers.UserStorageSyncManager.UserStorageProviderClusterEvent
    public static final int USER_STORAGE_PROVIDER_CLUSTER_EVENT = INFINISPAN_MAX_RESERVED_ID + 5;

    // clustering.infinispan package
    public static final int LOCK_ENTRY = INFINISPAN_MAX_RESERVED_ID + 6;
    public static final int LOCK_ENTRY_PREDICATE = INFINISPAN_MAX_RESERVED_ID + 7;
    public static final int WRAPPED_CLUSTER_EVENT = INFINISPAN_MAX_RESERVED_ID + 8;

    // keys.infinispan package
    public static final int PUBLIC_KEY_INVALIDATION_EVENT = INFINISPAN_MAX_RESERVED_ID + 9;

    //models.cache.infinispan.authorization.events package
    public static final int POLICY_EVENT = INFINISPAN_MAX_RESERVED_ID + 10;
    public static final int POLICY_UPDATED_EVENT = INFINISPAN_MAX_RESERVED_ID + 11;
    public static final int RESOURCE_EVENT = INFINISPAN_MAX_RESERVED_ID + 12;
    public static final int RESOURCE_UPDATED_EVENT = INFINISPAN_MAX_RESERVED_ID + 13;
    public static final int RESOURCE_SERVER_EVENT = INFINISPAN_MAX_RESERVED_ID + 14;
    public static final int RESOURCE_SERVER_UPDATED_EVENT = INFINISPAN_MAX_RESERVED_ID + 15;
    public static final int SCOPE_EVENT = INFINISPAN_MAX_RESERVED_ID + 16;
    public static final int SCOPE_UPDATED_EVENT = INFINISPAN_MAX_RESERVED_ID + 17;

    // models.sessions.infinispan.initializer package
    public static final int INITIALIZER_STATE = INFINISPAN_MAX_RESERVED_ID + 18;

    // models.sessions.infinispan.changes package
    public static final int SESSION_ENTITY_WRAPPER = INFINISPAN_MAX_RESERVED_ID + 19;

    // models.sessions.infinispan.changes.sessions package
    public static final int LAST_SESSION_REFRESH_EVENT = INFINISPAN_MAX_RESERVED_ID + 20;
    public static final int SESSION_DATA = INFINISPAN_MAX_RESERVED_ID + 21;

    // models.cache.infinispan.authorization.stream package
    public static final int IN_RESOURCE_PREDICATE = INFINISPAN_MAX_RESERVED_ID + 22;
    public static final int IN_RESOURCE_SERVER_PREDICATE = INFINISPAN_MAX_RESERVED_ID + 23;
    public static final int IN_SCOPE_PREDICATE = INFINISPAN_MAX_RESERVED_ID + 24;

    // models.sessions.infinispan.events package
    public static final int CLIENT_SESSION_REMOVED_EVENT = INFINISPAN_MAX_RESERVED_ID + 25;
    public static final int REALM_REMOVED_SESSION_EVENT = INFINISPAN_MAX_RESERVED_ID + 26;
    public static final int REMOVE_ALL_USER_LOGIN_FAILURES_EVENT = INFINISPAN_MAX_RESERVED_ID + 27;
    public static final int REMOVE_ALL_USER_SESSIONS_EVENT = INFINISPAN_MAX_RESERVED_ID + 28;

    // models.sessions.infinispan.stream package
    public static final int AUTHENTICATED_CLIENT_SESSION_PREDICATE = INFINISPAN_MAX_RESERVED_ID + 29;
    public static final int ROOT_AUTHENTICATION_SESSION_PREDICATE = INFINISPAN_MAX_RESERVED_ID + 30;
    public static final int SESSION_PREDICATE = INFINISPAN_MAX_RESERVED_ID + 31;
    public static final int USER_LOGIN_FAILURE_PREDICATE = INFINISPAN_MAX_RESERVED_ID + 32;
    public static final int USER_SESSION_PREDICATE = INFINISPAN_MAX_RESERVED_ID + 33;

    // models.cache.infinispan.stream package
    public static final int GROUP_LIST_PREDICATE = INFINISPAN_MAX_RESERVED_ID + 34;
    public static final int HAS_ROLE_PREDICATE = INFINISPAN_MAX_RESERVED_ID + 35;
    public static final int IN_CLIENT_PREDICATE = INFINISPAN_MAX_RESERVED_ID + 36;
    public static final int IN_GROUP_PREDICATE = INFINISPAN_MAX_RESERVED_ID + 37;
    public static final int IN_IDENTITY_PROVIDER_PREDICATE = INFINISPAN_MAX_RESERVED_ID + 38;
    public static final int IN_REALM_PREDICATE = INFINISPAN_MAX_RESERVED_ID + 39;

    // models.cache.infinispan.events package
    public static final int AUTHENTICATION_SESSION_AUTH_NOTE_UPDATE_EVENT = INFINISPAN_MAX_RESERVED_ID + 40;
    public static final int CLIENT_EVENT = INFINISPAN_MAX_RESERVED_ID + 41;
    public static final int CLIENT_REMOVED_EVENT = INFINISPAN_MAX_RESERVED_ID + 42;
    public static final int CLIENT_SCOPE_EVENT = INFINISPAN_MAX_RESERVED_ID + 43;
    public static final int CLIENT_SCOPE_REMOVED_EVENT = INFINISPAN_MAX_RESERVED_ID + 44;
    public static final int CLIENT_TEMPLATE_EVENT = INFINISPAN_MAX_RESERVED_ID + 45;
    public static final int CLIENT_UPDATED_EVENT = INFINISPAN_MAX_RESERVED_ID + 46;
    public static final int GROUP_ADDED_EVENT = INFINISPAN_MAX_RESERVED_ID + 47;
    public static final int GROUP_MOVED_EVENT = INFINISPAN_MAX_RESERVED_ID + 48;
    public static final int GROUP_REMOVED_EVENT = INFINISPAN_MAX_RESERVED_ID + 49;
    public static final int GROUP_UPDATED_EVENT = INFINISPAN_MAX_RESERVED_ID + 50;
    public static final int REALM_EVENT = INFINISPAN_MAX_RESERVED_ID + 51;
    public static final int REALM_UPDATED_EVENT = INFINISPAN_MAX_RESERVED_ID + 52;
    public static final int ROLE_ADDED_EVENT = INFINISPAN_MAX_RESERVED_ID + 53;
    public static final int ROLE_REMOVED_EVENT = INFINISPAN_MAX_RESERVED_ID + 54;
    public static final int ROLE_UPDATED_EVENT = INFINISPAN_MAX_RESERVED_ID + 55;
    public static final int USER_CACHE_REALM_INVALIDATION_EVENT = INFINISPAN_MAX_RESERVED_ID + 56;
    public static final int USER_CONSENTS_UPDATED_EVENT = INFINISPAN_MAX_RESERVED_ID + 57;
    public static final int USER_FEDERATION_LINK_REMOVED_EVENT = INFINISPAN_MAX_RESERVED_ID + 58;
    public static final int USER_FEDERATION_LINK_UPDATED_EVENT = INFINISPAN_MAX_RESERVED_ID + 59;
    public static final int USER_FULL_INVALIDATION_EVENT = INFINISPAN_MAX_RESERVED_ID + 60;
    public static final int USER_UPDATED_EVENT = INFINISPAN_MAX_RESERVED_ID + 61;

    // sessions.infinispan.entities package
    public static final int AUTHENTICATED_CLIENT_SESSION_STORE = INFINISPAN_MAX_RESERVED_ID + 62;
    public static final int AUTHENTICATED_CLIENT_SESSION_ENTITY = INFINISPAN_MAX_RESERVED_ID + 63;
    public static final int AUTHENTICATION_SESSION_ENTITY = INFINISPAN_MAX_RESERVED_ID + 64;
    public static final int LOGIN_FAILURE_ENTITY = INFINISPAN_MAX_RESERVED_ID + 65;
    public static final int LOGIN_FAILURE_KEY = INFINISPAN_MAX_RESERVED_ID + 66;
    public static final int ROOT_AUTHENTICATION_SESSION_ENTITY = INFINISPAN_MAX_RESERVED_ID + 67;
    public static final int SINGLE_USE_OBJECT_VALUE_ENTITY = INFINISPAN_MAX_RESERVED_ID + 68;
    public static final int USER_SESSION_ENTITY = INFINISPAN_MAX_RESERVED_ID + 69;

    public static final int REPLACE_FUNCTION = INFINISPAN_MAX_RESERVED_ID + 70;

    public static void configure(GlobalConfigurationBuilder builder) {
        builder.serialization()
                .addContextInitializer(KeycloakModelSchema.INSTANCE);
    }


    public static String emptyStringToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
