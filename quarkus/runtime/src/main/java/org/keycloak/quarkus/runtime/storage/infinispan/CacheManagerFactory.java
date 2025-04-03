/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.quarkus.runtime.storage.infinispan;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.RemoteCacheManagerAdmin;
import org.infinispan.client.hotrod.impl.ConfigurationProperties;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.commons.internal.InternalCacheNames;
import org.infinispan.commons.util.concurrent.CompletableFutures;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.protostream.descriptors.FileDescriptor;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;
import org.jboss.logging.Logger;
import org.keycloak.config.CachingOptions;
import org.keycloak.infinispan.util.InfinispanUtils;
import org.keycloak.marshalling.KeycloakIndexSchemaUtil;
import org.keycloak.marshalling.KeycloakModelSchema;
import org.keycloak.marshalling.Marshalling;
import org.keycloak.models.sessions.infinispan.query.ClientSessionQueries;
import org.keycloak.models.sessions.infinispan.query.UserSessionQueries;
import org.keycloak.models.sessions.infinispan.remote.RemoteInfinispanAuthenticationSessionProviderFactory;
import org.keycloak.models.sessions.infinispan.remote.RemoteUserLoginFailureProviderFactory;
import org.keycloak.quarkus.runtime.configuration.Configuration;

import javax.net.ssl.SSLContext;

import static org.keycloak.config.CachingOptions.CACHE_REMOTE_HOST_PROPERTY;
import static org.keycloak.config.CachingOptions.CACHE_REMOTE_PASSWORD_PROPERTY;
import static org.keycloak.config.CachingOptions.CACHE_REMOTE_PORT_PROPERTY;
import static org.keycloak.config.CachingOptions.CACHE_REMOTE_USERNAME_PROPERTY;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.AUTHENTICATION_SESSIONS_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.CLIENT_SESSION_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.CLUSTERED_CACHE_NAMES;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.LOGIN_FAILURE_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.OFFLINE_CLIENT_SESSION_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.OFFLINE_USER_SESSION_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.USER_SESSION_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.skipSessionsCacheIfRequired;
import static org.wildfly.security.sasl.util.SaslMechanismInformation.Names.SCRAM_SHA_512;

public class CacheManagerFactory {

    public static final Logger logger = Logger.getLogger(CacheManagerFactory.class);
    // Map with the default cache configuration if the cache is not present in the XML.

    private final CompletableFuture<RemoteCacheManager> remoteCacheManagerFuture;

    public CacheManagerFactory() {
        if (InfinispanUtils.isRemoteInfinispan()) {
            logger.debug("Remote Cache feature is enabled");
            this.remoteCacheManagerFuture = CompletableFuture.supplyAsync(this::startRemoteCacheManager);
        } else {
            logger.debug("Remote Cache feature is disabled");
            this.remoteCacheManagerFuture = CompletableFutures.completedNull();
        }
    }

    public RemoteCacheManager getOrCreateRemoteCacheManager() {
        return join(remoteCacheManagerFuture);
    }

    private static <T> T join(Future<T> future) {
        try {
            return future.get(getStartTimeout(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException("Failed to start embedded or remote cache manager", e);
        }
    }

    private RemoteCacheManager startRemoteCacheManager() {
        logger.info("Starting Infinispan remote cache manager (Hot Rod Client)");
        String cacheRemoteHost = requiredStringProperty(CACHE_REMOTE_HOST_PROPERTY);
        Integer cacheRemotePort = Configuration.getOptionalKcValue(CACHE_REMOTE_PORT_PROPERTY)
                .map(Integer::parseInt)
                .orElse(ConfigurationProperties.DEFAULT_HOTROD_PORT);

        org.infinispan.client.hotrod.configuration.ConfigurationBuilder builder = new org.infinispan.client.hotrod.configuration.ConfigurationBuilder();
        builder.addServer().host(cacheRemoteHost).port(cacheRemotePort);
        builder.connectionPool().maxActive(16).exhaustedAction(org.infinispan.client.hotrod.configuration.ExhaustedAction.CREATE_NEW);

        if (isRemoteTLSEnabled()) {
            builder.security().ssl()
                    .enable()
                    .sslContext(createSSLContext())
                    .sniHostName(cacheRemoteHost);
        }

        if (isRemoteAuthenticationEnabled()) {
            builder.security().authentication()
                    .enable()
                    .username(requiredStringProperty(CACHE_REMOTE_USERNAME_PROPERTY))
                    .password(requiredStringProperty(CACHE_REMOTE_PASSWORD_PROPERTY))
                    .realm("default")
                    .saslMechanism(SCRAM_SHA_512);
        }

        Marshalling.configure(builder);

        if (shouldCreateRemoteCaches()) {
            createRemoteCaches(builder);
        }

        var remoteCacheManager = new RemoteCacheManager(builder.build());

        // update the schema before trying to access the caches
        updateProtoSchema(remoteCacheManager);

        // establish connection to all caches
        if (isStartEagerly()) {
            skipSessionsCacheIfRequired(Arrays.stream(CLUSTERED_CACHE_NAMES)).forEach(remoteCacheManager::getCache);
        }
        return remoteCacheManager;
    }

    private static void createRemoteCaches(org.infinispan.client.hotrod.configuration.ConfigurationBuilder builder) {
        // fall back for distributed caches if not defined
        logger.warn("Creating remote cache in external Infinispan server. It should not be used in production!");
        var baseConfig = defaultRemoteCacheBuilder().build();

        skipSessionsCacheIfRequired(Arrays.stream(CLUSTERED_CACHE_NAMES))
                .forEach(name -> builder.remoteCache(name).configuration(baseConfig.toStringConfiguration(name)));
    }

    private static ConfigurationBuilder defaultRemoteCacheBuilder() {
        var builder = new ConfigurationBuilder();
        builder.clustering().cacheMode(CacheMode.DIST_SYNC);
        builder.encoding().mediaType(MediaType.APPLICATION_PROTOSTREAM);
        return builder;
    }

    private void updateProtoSchema(RemoteCacheManager remoteCacheManager) {
        var key = KeycloakModelSchema.INSTANCE.getProtoFileName();
        var current = KeycloakModelSchema.INSTANCE.getProtoFile();

        RemoteCache<String, String> protostreamMetadataCache = remoteCacheManager.getCache(InternalCacheNames.PROTOBUF_METADATA_CACHE_NAME);
        var stored = protostreamMetadataCache.getWithMetadata(key);
        if (stored == null) {
            if (protostreamMetadataCache.putIfAbsent(key, current) == null) {
                logger.info("Infinispan ProtoStream schema uploaded for the first time.");
            } else {
                logger.info("Failed to update Infinispan ProtoStream schema. Assumed it was updated by other Keycloak server.");
            }
            checkForProtoSchemaErrors(protostreamMetadataCache);
            return;
        }
        if (Objects.equals(stored.getValue(), current)) {
            logger.info("Infinispan ProtoStream schema is up to date!");
            return;
        }
        if (protostreamMetadataCache.replaceWithVersion(key, current, stored.getVersion())) {
            logger.info("Infinispan ProtoStream schema successful updated.");
            reindexCaches(remoteCacheManager, stored.getValue(), current);
        } else {
            logger.info("Failed to update Infinispan ProtoStream schema. Assumed it was updated by other Keycloak server.");
        }
        checkForProtoSchemaErrors(protostreamMetadataCache);
    }

    private void checkForProtoSchemaErrors(RemoteCache<String, String> protostreamMetadataCache) {
        String errors = protostreamMetadataCache.get(ProtobufMetadataManagerConstants.ERRORS_KEY_SUFFIX);
        if (errors != null) {
            for (String errorFile : errors.split("\n")) {
                logger.errorf("%nThere was an error in proto file: %s%nError message: %s%nCurrent proto schema: %s%n",
                        errorFile,
                        protostreamMetadataCache.get(errorFile + ProtobufMetadataManagerConstants.ERRORS_KEY_SUFFIX),
                        protostreamMetadataCache.get(errorFile));
            }
        }
    }

    private static void reindexCaches(RemoteCacheManager remoteCacheManager, String oldSchema, String newSchema) {
        var oldPS = KeycloakModelSchema.parseProtoSchema(oldSchema);
        var newPS = KeycloakModelSchema.parseProtoSchema(newSchema);
        var admin = remoteCacheManager.administration();

        if (isEntityChanged(oldPS, newPS, RemoteUserLoginFailureProviderFactory.PROTO_ENTITY)) {
            updateSchemaAndReIndexCache(admin, LOGIN_FAILURE_CACHE_NAME);
        }

        if (isEntityChanged(oldPS, newPS, RemoteInfinispanAuthenticationSessionProviderFactory.PROTO_ENTITY)) {
            updateSchemaAndReIndexCache(admin, AUTHENTICATION_SESSIONS_CACHE_NAME);
        }

        if (isEntityChanged(oldPS, newPS, ClientSessionQueries.CLIENT_SESSION)) {
            updateSchemaAndReIndexCache(admin, CLIENT_SESSION_CACHE_NAME);
            updateSchemaAndReIndexCache(admin, OFFLINE_CLIENT_SESSION_CACHE_NAME);
        }

        if (isEntityChanged(oldPS, newPS, UserSessionQueries.USER_SESSION)) {
            updateSchemaAndReIndexCache(admin, USER_SESSION_CACHE_NAME);
            updateSchemaAndReIndexCache(admin, OFFLINE_USER_SESSION_CACHE_NAME);
        }
    }

    private static boolean isEntityChanged(FileDescriptor oldSchema, FileDescriptor newSchema, String entity) {
        var v1 = KeycloakModelSchema.findEntity(oldSchema, entity);
        var v2 = KeycloakModelSchema.findEntity(newSchema, entity);
        return v1.isPresent() && v2.isPresent() && KeycloakIndexSchemaUtil.isIndexSchemaChanged(v1.get(), v2.get());
    }

    private static void updateSchemaAndReIndexCache(RemoteCacheManagerAdmin admin, String cacheName) {
        admin.updateIndexSchema(cacheName);
        admin.reindexCache(cacheName);
    }

    private static boolean isRemoteTLSEnabled() {
        return Configuration.isTrue(CachingOptions.CACHE_REMOTE_TLS_ENABLED);
    }

    private static boolean isRemoteAuthenticationEnabled() {
        return Configuration.getOptionalKcValue(CACHE_REMOTE_USERNAME_PROPERTY).isPresent() ||
                Configuration.getOptionalKcValue(CACHE_REMOTE_PASSWORD_PROPERTY).isPresent();
    }

    private static boolean shouldCreateRemoteCaches() {
        return Boolean.getBoolean("kc.cache-remote-create-caches");
    }

    private static SSLContext createSSLContext() {
        try {
            // uses the default Java Runtime TrustStore, or the one generated by Keycloak (see org.keycloak.truststore.TruststoreBuilder)
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isStartEagerly() {
        // eagerly starts caches by default
        return Boolean.parseBoolean(System.getProperty("kc.cache-ispn-start-eagerly", Boolean.TRUE.toString()));
    }

    private static int getStartTimeout() {
        return Integer.getInteger("kc.cache-ispn-start-timeout", 120);
    }

    public static String requiredStringProperty(String propertyName) {
        return Configuration.getOptionalKcValue(propertyName).orElseThrow(() -> new RuntimeException("Property " + propertyName + " required but not specified"));
    }

}
