package org.keycloak.models.sessions.infinispan.remote;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.commons.util.concurrent.CompletionStages;
import org.keycloak.Config;
import org.keycloak.common.util.Environment;
import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.infinispan.util.InfinispanUtils;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionProvider;
import org.keycloak.models.UserSessionProviderFactory;
import org.keycloak.models.session.UserSessionPersisterProvider;
import org.keycloak.models.sessions.infinispan.changes.remote.RemoteChangeLogTransaction;
import org.keycloak.models.sessions.infinispan.changes.remote.UserSessionTransaction;
import org.keycloak.models.sessions.infinispan.changes.remote.updater.client.AuthenticatedClientSessionUpdater;
import org.keycloak.models.sessions.infinispan.changes.remote.updater.user.UserSessionUpdater;
import org.keycloak.models.sessions.infinispan.entities.AuthenticatedClientSessionEntity;
import org.keycloak.models.sessions.infinispan.entities.UserSessionEntity;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.provider.ProviderEvent;
import org.keycloak.provider.ProviderEventListener;

public class RemoteUserSessionProviderFactory implements UserSessionProviderFactory<RemoteUserSessionProvider>, EnvironmentDependentProviderFactory, ProviderEventListener {

    private static final int DEFAULT_BATCH_SIZE = 1024;
    private static final String CONFIG_MAX_BATCH_SIZE = "batchSize";

    private volatile RemoteCacheHolder remoteCacheHolder;
    private int batchSize = DEFAULT_BATCH_SIZE;

    @Override
    public RemoteUserSessionProvider create(KeycloakSession session) {
        var tx = createTransaction(session);
        session.getTransactionManager().enlistAfterCompletion(tx);
        return new RemoteUserSessionProvider(session, tx, batchSize);
    }

    @Override
    public void init(Config.Scope config) {
        batchSize = config.getInt(CONFIG_MAX_BATCH_SIZE, DEFAULT_BATCH_SIZE);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        try (var session = factory.create()) {
            lazyInit(session);
        }
        factory.register(this);

    }

    @Override
    public void close() {
        remoteCacheHolder = null;
    }

    @Override
    public String getId() {
        return InfinispanUtils.REMOTE_PROVIDER_ID;
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return InfinispanUtils.isRemoteInfinispan();
    }

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {
        ProviderConfigurationBuilder builder = ProviderConfigurationBuilder.create();

        builder.property()
                .name(CONFIG_MAX_BATCH_SIZE)
                .type("int")
                .helpText("Batch size when streaming session from the remote cache")
                .defaultValue(DEFAULT_BATCH_SIZE)
                .add();
        return builder.build();
    }

    @Override
    public void onEvent(ProviderEvent event) {
        if ((event instanceof PostMigrationEvent pme)) {
            onPostMigration(pme);
            return;
        }
        if (event instanceof UserModel.UserRemovedEvent ure) {
            onUserRemoved(ure);
        }
    }

    private void onPostMigration(PostMigrationEvent event) {
        KeycloakModelUtils.runJobInTransactionWithTimeout(event.getFactory(), this::preloadUserSessions, Environment.getServerStartupTimeout());
    }

    private void preloadUserSessions(KeycloakSession session) {
        preloadUserSessions(session, true);
        preloadUserSessions(session, false);
    }

    private void preloadUserSessions(KeycloakSession session, boolean offline) {
        var userSessionCache = remoteCacheHolder.userSessionCache(offline);
        var clientSessionCache = remoteCacheHolder.clientSessionCache(offline);
        var database = session.getProvider(UserSessionPersisterProvider.class);
        List<String> userSessionIds = Collections.synchronizedList(new LinkedList<>());
        List<Map.Entry<String, String>> clientSessionIds = Collections.synchronizedList(new LinkedList<>());
        var stage = CompletionStages.aggregateCompletionStage();
        database.loadUserSessionsStream(-1, -1, offline, "").forEach(userSessionModel -> {
            var userSessionEntity = UserSessionEntity.createFromModel(userSessionModel);
            stage.dependsOn(userSessionCache.putIfAbsentAsync(userSessionModel.getId(), userSessionEntity));
            userSessionIds.add(userSessionModel.getId());
            for (var clientSessionModel : userSessionModel.getAuthenticatedClientSessions().values()) {
                clientSessionIds.add(Map.entry(userSessionModel.getId(), clientSessionModel.getId()));
                var clientSessionEntity = AuthenticatedClientSessionEntity.createFromModel(clientSessionModel);
                stage.dependsOn(clientSessionCache.putIfAbsentAsync(clientSessionEntity.getId(), clientSessionEntity));
            }
        });
        CompletionStages.join(stage.freeze());
        userSessionIds.forEach(s -> database.removeUserSession(s, offline));
        clientSessionIds.forEach(e -> database.removeClientSession(e.getKey(), e.getValue(), offline));
    }

    private void onUserRemoved(UserModel.UserRemovedEvent event) {
        event.getKeycloakSession().getProvider(UserSessionProvider.class, getId()).removeUserSessions(event.getRealm(), event.getUser());
        event.getKeycloakSession().getProvider(UserSessionPersisterProvider.class).onUserRemoved(event.getRealm(), event.getUser());

    }

    private void lazyInit(KeycloakSession session) {
        if (remoteCacheHolder != null) {
            return;
        }
        InfinispanConnectionProvider connections = session.getProvider(InfinispanConnectionProvider.class);
        RemoteCache<String, UserSessionEntity> userSessionCache = connections.getRemoteCache(InfinispanConnectionProvider.USER_SESSION_CACHE_NAME);
        RemoteCache<String, UserSessionEntity> offlineUserSessionsCache = connections.getRemoteCache(InfinispanConnectionProvider.OFFLINE_USER_SESSION_CACHE_NAME);
        RemoteCache<UUID, AuthenticatedClientSessionEntity> clientSessionCache = connections.getRemoteCache(InfinispanConnectionProvider.CLIENT_SESSION_CACHE_NAME);
        RemoteCache<UUID, AuthenticatedClientSessionEntity> offlineClientSessionsCache = connections.getRemoteCache(InfinispanConnectionProvider.OFFLINE_CLIENT_SESSION_CACHE_NAME);
        remoteCacheHolder = new RemoteCacheHolder(userSessionCache, offlineUserSessionsCache, clientSessionCache, offlineClientSessionsCache);
    }

    private UserSessionTransaction createTransaction(KeycloakSession session) {
        lazyInit(session);
        return new UserSessionTransaction(
                createUserSessionTransaction(false, session),
                createUserSessionTransaction(true, session),
                createClientSessionTransaction(false, session),
                createClientSessionTransaction(true, session)
        );
    }

    private RemoteChangeLogTransaction<String, UserSessionEntity, UserSessionUpdater> createUserSessionTransaction(boolean offline, KeycloakSession session) {
        return new RemoteChangeLogTransaction<>(UserSessionUpdater.factory(offline), remoteCacheHolder.userSessionCache(offline), session);
    }

    private RemoteChangeLogTransaction<UUID, AuthenticatedClientSessionEntity, AuthenticatedClientSessionUpdater> createClientSessionTransaction(boolean offline, KeycloakSession session) {
        return new RemoteChangeLogTransaction<>(AuthenticatedClientSessionUpdater.factory(offline), remoteCacheHolder.clientSessionCache(offline), session);
    }

    private record RemoteCacheHolder(
            RemoteCache<String, UserSessionEntity> userSession,
            RemoteCache<String, UserSessionEntity> offlineUserSession,
            RemoteCache<UUID, AuthenticatedClientSessionEntity> clientSession,
            RemoteCache<UUID, AuthenticatedClientSessionEntity> offlineClientSession) {

        RemoteCache<String, UserSessionEntity> userSessionCache(boolean offline) {
            return offline ? offlineUserSession : userSession;
        }

        RemoteCache<UUID, AuthenticatedClientSessionEntity> clientSessionCache(boolean offline) {
            return offline ? offlineClientSession : clientSession;
        }
    }
}
