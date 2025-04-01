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

package org.keycloak.spi.infinispan.impl.embedded;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.micrometer.core.instrument.Metrics;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.StatisticsConfigurationBuilder;
import org.infinispan.configuration.global.ShutdownHookBehavior;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.metrics.config.MicrometerMeterRegisterConfigurationBuilder;
import org.infinispan.persistence.remote.configuration.RemoteStoreConfigurationBuilder;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.common.Profile;
import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.connections.infinispan.InfinispanUtil;
import org.keycloak.infinispan.util.InfinispanUtils;
import org.keycloak.marshalling.Marshalling;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.spi.infinispan.CacheEmbeddedConfigProvider;
import org.keycloak.spi.infinispan.CacheEmbeddedConfigProviderFactory;
import org.keycloak.spi.infinispan.JGroupsCertificateProvider;

import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.CLUSTERED_CACHE_NAMES;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.CRL_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.LOCAL_CACHE_NAMES;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.WORK_CACHE_NAME;

public class DefaultCacheEmbeddedConfigProviderFactory implements CacheEmbeddedConfigProviderFactory, CacheEmbeddedConfigProvider {

    private static final Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());
    // Map with the default cache configuration if the cache is not present in the XML.
    private static final Map<String, Supplier<ConfigurationBuilder>> DEFAULT_CONFIGS = Map.of(
            CRL_CACHE_NAME, InfinispanUtil::getCrlCacheConfig
    );
    private static final Supplier<ConfigurationBuilder> TO_NULL = () -> null;

    private volatile ConfigurationBuilderHolder builderHolder;
    private volatile Config.Scope keycloakConfig;

    @Override
    public CacheEmbeddedConfigProvider create(KeycloakSession session) {
        lazyInit(session.getKeycloakSessionFactory());
        return this;
    }

    @Override
    public void init(Config.Scope config) {
        this.keycloakConfig = config;
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        lazyInit(factory);
    }

    @Override
    public ConfigurationBuilderHolder configuration() {
        return builderHolder;
    }

    @Override
    public void close() {
        //no-op
    }

    @Override
    public String getId() {
        return "default";
    }

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {
        return CacheEmbeddedConfigProviderFactory.super.getConfigMetadata();
    }

    @Override
    public Set<Class<? extends Provider>> dependsOn() {
        return Set.of(JGroupsCertificateProvider.class);
    }

    private void lazyInit(KeycloakSessionFactory factory) {
        if (builderHolder != null) {
            return;
        }
        synchronized (this) {
            if (builderHolder != null) {
                return;
            }
            builderHolder = Objects.requireNonNull(createConfiguration(factory));
        }
    }

    protected ConfigurationBuilderHolder createConfiguration(KeycloakSessionFactory factory) {
        if (InfinispanUtils.isRemoteInfinispan()) {
            return createMultiSiteConfiguration(keycloakConfig);
        }
        if (Profile.isFeatureEnabled(Profile.Feature.PERSISTENT_USER_SESSIONS)) {
            return createPersistentUserSessionsConfiguration(keycloakConfig, factory);
        }
        return createVolatileUserSessionsConfiguration(keycloakConfig, factory);
    }

    private static ConfigurationBuilderHolder createVolatileUserSessionsConfiguration(Config.Scope keycloakConfig, KeycloakSessionFactory factory) {
        var holder = parseConfiguration(keycloakConfig);
        singleSiteConfiguration(holder);
        configureCacheMaxCount(keycloakConfig, holder, Arrays.stream(InfinispanConnectionProvider.LOCAL_MAX_COUNT_CACHES));
        configureMetrics(keycloakConfig, holder);
        return holder;
    }

    private static ConfigurationBuilderHolder createPersistentUserSessionsConfiguration(Config.Scope keycloakConfig, KeycloakSessionFactory factory) {
        var holder = parseConfiguration(keycloakConfig);
        singleSiteConfiguration(holder);
        configureCacheMaxCount(keycloakConfig, holder,
                Stream.concat(
                        Arrays.stream(InfinispanConnectionProvider.LOCAL_MAX_COUNT_CACHES),
                        Arrays.stream(InfinispanConnectionProvider.CLUSTERED_MAX_COUNT_CACHES)
                ));
        configureMetrics(keycloakConfig, holder);
        return holder;
    }

    private static ConfigurationBuilderHolder createMultiSiteConfiguration(Config.Scope keycloakConfig) {
        var holder = parseConfiguration(keycloakConfig);
        removeClusteredCaches(holder);
        checkForRemoteStores(holder);
        checkOrConfigureCaches(holder, Arrays.stream(LOCAL_CACHE_NAMES));
        // Disable JGroups, not required when the data is stored in the Remote Cache.
        // The existing caches are local and do not require JGroups to work properly.
        holder.getGlobalConfigurationBuilder().nonClusteredDefault();
        configureCacheMaxCount(keycloakConfig, holder, Arrays.stream(InfinispanConnectionProvider.LOCAL_MAX_COUNT_CACHES));
        configureMetrics(keycloakConfig, holder);
        return holder;
    }

    private static ConfigurationBuilderHolder parseConfiguration(Config.Scope keycloakConfig) {
        var config = keycloakConfig.get("config");
        var holder = new ParserRegistry().parse(config);
        // We must disable the Infinispan default ShutdownHook as we manage the EmbeddedCacheManager lifecycle explicitly
        // with #shutdown and multiple calls to EmbeddedCacheManager#stop can lead to Exceptions being thrown
        holder.getGlobalConfigurationBuilder().shutdown().hookBehavior(ShutdownHookBehavior.DONT_REGISTER);
        Marshalling.configure(holder.getGlobalConfigurationBuilder());
        return holder;
    }

    private static void singleSiteConfiguration(ConfigurationBuilderHolder holder) {
        validateWorkCacheConfiguration(holder);
        checkForRemoteStores(holder);
        checkOrConfigureCaches(holder, Stream.concat(
                Arrays.stream(LOCAL_CACHE_NAMES),
                Arrays.stream(CLUSTERED_CACHE_NAMES)
        ));

    }

    private static void validateWorkCacheConfiguration(ConfigurationBuilderHolder builder) {
        var cacheBuilder  = builder.getNamedConfigurationBuilders().get(WORK_CACHE_NAME);
        if (cacheBuilder == null) {
            throw new RuntimeException("Unable to start Keycloak. '%s' cache is missing".formatted(WORK_CACHE_NAME));
        }
        if (builder.getGlobalConfigurationBuilder().cacheContainer().transport().getTransport() == null) {
            // non-clustered, Keycloak started in dev mode?
            return;
        }
        var cacheMode = cacheBuilder.clustering().cacheMode();
        if (!cacheMode.isReplicated()) {
            throw new RuntimeException("Unable to start Keycloak. '%s' cache must be replicated but is %s".formatted(WORK_CACHE_NAME, cacheMode.friendlyCacheModeString().toLowerCase()));
        }
    }

    private static void removeClusteredCaches(ConfigurationBuilderHolder holder) {
        Arrays.stream(InfinispanConnectionProvider.CLUSTERED_CACHE_NAMES)
                .forEach(holder.getNamedConfigurationBuilders()::remove);
    }

    private static void configureCacheMaxCount(Config.Scope keycloakConfig, ConfigurationBuilderHolder holder, Stream<String> caches) {
        caches.forEach(name -> {
            var maxCount = keycloakConfig.getInt("maxCount" + name);
            if (maxCount != null) {
                holder.getNamedConfigurationBuilders().get(name).memory().maxCount(maxCount);
            }
        });
    }

    private static void configureMetrics(Config.Scope keycloakConfig, ConfigurationBuilderHolder holder) {
        if (keycloakConfig.getBoolean("metricsEnabled", Boolean.FALSE)) {
            holder.getGlobalConfigurationBuilder().addModule(MicrometerMeterRegisterConfigurationBuilder.class)
                    .meterRegistry(Metrics.globalRegistry);
            holder.getGlobalConfigurationBuilder().cacheContainer().statistics(true);
            holder.getGlobalConfigurationBuilder().metrics().namesAsTags(true);
            holder.getGlobalConfigurationBuilder().metrics().histograms(keycloakConfig.getBoolean("histogramEnabled", Boolean.FALSE));
            holder.getNamedConfigurationBuilders()
                    .values()
                    .stream()
                    .map(ConfigurationBuilder::statistics)
                    .forEach(StatisticsConfigurationBuilder::enable);
        }
    }

    private static void checkOrConfigureCaches(ConfigurationBuilderHolder holder, Stream<String> caches)  {
        for (var it = caches.iterator() ; it.hasNext() ; ) {
            var cache = it.next();
            var builder = holder.getNamedConfigurationBuilders().get(cache);
            if (builder != null) {
                continue;
            }
            builder = DEFAULT_CONFIGS.getOrDefault(cache, TO_NULL).get();
            if (builder == null) {
                throw new IllegalStateException("Infinispan cache '%s' not found. Make sure it is defined in your XML configuration file.".formatted(cache));
            }
            holder.getNamedConfigurationBuilders().put(cache, builder);
        }
    }

    private static void checkForRemoteStores(ConfigurationBuilderHolder builder) {
        if (Profile.isFeatureEnabled(Profile.Feature.CACHE_EMBEDDED_REMOTE_STORE) && Profile.isFeatureEnabled(Profile.Feature.MULTI_SITE)) {
            logger.fatalf("Feature %s is now deprecated.%nFor multi-site (cross-dc) support, enable only %s.",
                    Profile.Feature.CACHE_EMBEDDED_REMOTE_STORE.getKey(), Profile.Feature.MULTI_SITE.getKey());
            throw new RuntimeException("The features " + Profile.Feature.CACHE_EMBEDDED_REMOTE_STORE.getKey() + " and " + Profile.Feature.MULTI_SITE.getKey() + " must not be enabled at the same time.");
        }
        if (Profile.isFeatureEnabled(Profile.Feature.CACHE_EMBEDDED_REMOTE_STORE) && Profile.isFeatureEnabled(Profile.Feature.CLUSTERLESS)) {
            logger.fatalf("Feature %s is now deprecated.%nFor multi-site (cross-dc) support, enable only %s.",
                    Profile.Feature.CACHE_EMBEDDED_REMOTE_STORE.getKey(), Profile.Feature.CLUSTERLESS.getKey());
            throw new RuntimeException("The features " + Profile.Feature.CACHE_EMBEDDED_REMOTE_STORE.getKey() + " and " + Profile.Feature.CLUSTERLESS.getKey() + " must not be enabled at the same time.");
        }
        if (!Profile.isFeatureEnabled(Profile.Feature.CACHE_EMBEDDED_REMOTE_STORE)) {
            if (builder.getNamedConfigurationBuilders().values().stream().anyMatch(DefaultCacheEmbeddedConfigProviderFactory::hasRemoteStore)) {
                logger.fatalf("Remote stores are not supported for embedded caches as feature %s is not enabled. This feature is disabled by default as it is now deprecated.%nFor keeping user sessions across restarts, use feature %s which is enabled by default.%nFor multi-site (cross-dc) support, enable %s.",
                        Profile.Feature.CACHE_EMBEDDED_REMOTE_STORE.getKey(), Profile.Feature.PERSISTENT_USER_SESSIONS.getKey(), Profile.Feature.MULTI_SITE.getKey());
                throw new RuntimeException("Remote store is not supported as feature " + Profile.Feature.CACHE_EMBEDDED_REMOTE_STORE.getKey() + " is not enabled.");
            }
        }
    }

    private static boolean hasRemoteStore(ConfigurationBuilder builder) {
        return builder.persistence().stores().stream().anyMatch(RemoteStoreConfigurationBuilder.class::isInstance);
    }
}
