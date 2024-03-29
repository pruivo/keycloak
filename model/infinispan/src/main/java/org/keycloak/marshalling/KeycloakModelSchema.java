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

import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.annotations.ProtoSchema;
import org.infinispan.protostream.annotations.ProtoSyntax;
import org.infinispan.protostream.types.java.CommonTypes;
import org.keycloak.cluster.infinispan.LockEntry;
import org.keycloak.cluster.infinispan.LockEntryPredicate;
import org.keycloak.cluster.infinispan.WrapperClusterEvent;
import org.keycloak.component.ComponentModel;
import org.keycloak.keys.infinispan.PublicKeyStorageInvalidationEvent;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.cache.infinispan.authorization.events.PolicyEvent;
import org.keycloak.models.cache.infinispan.authorization.events.ResourceEvent;
import org.keycloak.models.cache.infinispan.authorization.events.ResourceServerEvent;
import org.keycloak.models.cache.infinispan.authorization.events.ScopeEvent;
import org.keycloak.models.cache.infinispan.authorization.stream.InResourcePredicate;
import org.keycloak.models.cache.infinispan.authorization.stream.InResourceServerPredicate;
import org.keycloak.models.cache.infinispan.authorization.stream.InScopePredicate;
import org.keycloak.models.cache.infinispan.events.AuthenticationSessionAuthNoteUpdateEvent;
import org.keycloak.models.cache.infinispan.events.ClientEvent;
import org.keycloak.models.cache.infinispan.events.ClientScopeEvent;
import org.keycloak.models.cache.infinispan.events.GroupAddedEvent;
import org.keycloak.models.cache.infinispan.events.GroupMovedEvent;
import org.keycloak.models.cache.infinispan.events.GroupRemovedEvent;
import org.keycloak.models.cache.infinispan.events.GroupUpdatedEvent;
import org.keycloak.models.cache.infinispan.events.RealmEvent;
import org.keycloak.models.cache.infinispan.events.RoleEvent;
import org.keycloak.models.cache.infinispan.events.UserCacheRealmInvalidationEvent;
import org.keycloak.models.cache.infinispan.events.UserConsentsUpdatedEvent;
import org.keycloak.models.cache.infinispan.events.UserFederationLinkRemovedEvent;
import org.keycloak.models.cache.infinispan.events.UserFederationLinkUpdatedEvent;
import org.keycloak.models.cache.infinispan.events.UserFullInvalidationEvent;
import org.keycloak.models.cache.infinispan.events.UserUpdatedEvent;
import org.keycloak.models.cache.infinispan.stream.GroupListPredicate;
import org.keycloak.models.cache.infinispan.stream.HasRolePredicate;
import org.keycloak.models.cache.infinispan.stream.InClientPredicate;
import org.keycloak.models.cache.infinispan.stream.InGroupPredicate;
import org.keycloak.models.cache.infinispan.stream.InIdentityProviderPredicate;
import org.keycloak.models.cache.infinispan.stream.InRealmPredicate;
import org.keycloak.models.sessions.infinispan.changes.ReplaceFunction;
import org.keycloak.models.sessions.infinispan.changes.SessionEntityWrapper;
import org.keycloak.models.sessions.infinispan.changes.sessions.LastSessionRefreshEvent;
import org.keycloak.models.sessions.infinispan.changes.sessions.SessionData;
import org.keycloak.models.sessions.infinispan.entities.AuthenticatedClientSessionEntity;
import org.keycloak.models.sessions.infinispan.entities.AuthenticatedClientSessionStore;
import org.keycloak.models.sessions.infinispan.entities.AuthenticationSessionEntity;
import org.keycloak.models.sessions.infinispan.entities.LoginFailureEntity;
import org.keycloak.models.sessions.infinispan.entities.LoginFailureKey;
import org.keycloak.models.sessions.infinispan.entities.RootAuthenticationSessionEntity;
import org.keycloak.models.sessions.infinispan.entities.SingleUseObjectValueEntity;
import org.keycloak.models.sessions.infinispan.entities.UserSessionEntity;
import org.keycloak.models.sessions.infinispan.events.RealmRemovedSessionEvent;
import org.keycloak.models.sessions.infinispan.events.RemoveAllUserLoginFailuresEvent;
import org.keycloak.models.sessions.infinispan.events.RemoveUserSessionsEvent;
import org.keycloak.models.sessions.infinispan.initializer.InitializerState;
import org.keycloak.models.sessions.infinispan.stream.RootAuthenticationSessionPredicate;
import org.keycloak.models.sessions.infinispan.stream.SessionPredicate;
import org.keycloak.models.sessions.infinispan.stream.UserLoginFailurePredicate;
import org.keycloak.models.sessions.infinispan.stream.UserSessionPredicate;
import org.keycloak.sessions.CommonClientSessionModel;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.managers.UserStorageSyncManager;

@ProtoSchema(
        syntax = ProtoSyntax.PROTO3,
        schemaPackageName = "keycloak",
        schemaFilePath = "proto/generated",

        // common-types for UUID
        dependsOn = CommonTypes.class,

        includeClasses = {
                // Model
                UserSessionModel.State.class,
                CommonClientSessionModel.ExecutionStatus.class,
                ComponentModel.MultiMapEntry.class,
                UserStorageProviderModel.class,
                UserStorageSyncManager.UserStorageProviderClusterEvent.class,

                // clustering.infinispan package
                LockEntry.class,
                LockEntryPredicate.class,
                WrapperClusterEvent.class,

                // keys.infinispan package
                PublicKeyStorageInvalidationEvent.class,

                //models.cache.infinispan.authorization.events package
                PolicyEvent.class,
                ResourceEvent.class,
                ResourceServerEvent.class,
                ScopeEvent.class,

                // models.sessions.infinispan.initializer package
                InitializerState.class,

                // models.sessions.infinispan.changes package
                SessionEntityWrapper.class,

                // models.sessions.infinispan.changes.sessions package
                LastSessionRefreshEvent.class,
                SessionData.class,

                // models.cache.infinispan.authorization.stream package
                InResourcePredicate.class,
                InResourceServerPredicate.class,
                InScopePredicate.class,

                // models.sessions.infinispan.events package
                RealmRemovedSessionEvent.class,
                RemoveAllUserLoginFailuresEvent.class,
                RemoveUserSessionsEvent.class,

                // models.sessions.infinispan.stream package
                RootAuthenticationSessionPredicate.class,
                SessionPredicate.class,
                UserLoginFailurePredicate.class,
                UserSessionPredicate.class,

                // models.cache.infinispan.stream package
                GroupListPredicate.class,
                HasRolePredicate.class,
                InClientPredicate.class,
                InGroupPredicate.class,
                InIdentityProviderPredicate.class,
                InRealmPredicate.class,

                // models.cache.infinispan.events package
                AuthenticationSessionAuthNoteUpdateEvent.class,
                ClientEvent.class,
                ClientScopeEvent.class,
                GroupAddedEvent.class,
                GroupMovedEvent.class,
                GroupRemovedEvent.class,
                GroupUpdatedEvent.class,
                RealmEvent.class,
                RoleEvent.class,
                UserCacheRealmInvalidationEvent.class,
                UserConsentsUpdatedEvent.class,
                UserFederationLinkRemovedEvent.class,
                UserFederationLinkUpdatedEvent.class,
                UserFullInvalidationEvent.class,
                UserUpdatedEvent.class,


                // sessions.infinispan.entities package
                AuthenticatedClientSessionStore.class,
                AuthenticatedClientSessionEntity.class,
                AuthenticationSessionEntity.class,
                LoginFailureEntity.class,
                LoginFailureKey.class,
                RootAuthenticationSessionEntity.class,
                SingleUseObjectValueEntity.class,
                UserSessionEntity.class,
                ReplaceFunction.class
        }
)
public interface KeycloakModelSchema extends GeneratedSchema {

    KeycloakModelSchema INSTANCE = new KeycloakModelSchemaImpl();


}
