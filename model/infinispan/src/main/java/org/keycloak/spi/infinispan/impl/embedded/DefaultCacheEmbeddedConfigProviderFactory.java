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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.micrometer.core.instrument.Metrics;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.HashConfiguration;
import org.infinispan.configuration.cache.StatisticsConfigurationBuilder;
import org.infinispan.configuration.global.ShutdownHookBehavior;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.metrics.config.MicrometerMeterRegisterConfigurationBuilder;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.common.Profile;
import org.keycloak.common.util.MultiSiteUtils;
import org.keycloak.config.CachingOptions;
import org.keycloak.config.MetricsOptions;
import org.keycloak.config.Option;
import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.connections.infinispan.InfinispanUtil;
import org.keycloak.infinispan.util.InfinispanUtils;
import org.keycloak.marshalling.Marshalling;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.spi.infinispan.CacheEmbeddedConfigProvider;
import org.keycloak.spi.infinispan.CacheEmbeddedConfigProviderFactory;
import org.keycloak.spi.infinispan.JGroupsCertificateProvider;

import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.ALL_CACHES_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.AUTHORIZATION_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.AUTHORIZATION_REVISIONS_CACHE_DEFAULT_MAX;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.AUTHORIZATION_REVISIONS_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.CLIENT_SESSION_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.CLUSTERED_MAX_COUNT_CACHES;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.CRL_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.LOCAL_CACHE_NAMES;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.LOCAL_MAX_COUNT_CACHES;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.OFFLINE_CLIENT_SESSION_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.OFFLINE_USER_SESSION_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.REALM_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.REALM_REVISIONS_CACHE_DEFAULT_MAX;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.REALM_REVISIONS_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.USER_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.USER_REVISIONS_CACHE_DEFAULT_MAX;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.USER_REVISIONS_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.USER_SESSION_CACHE_NAME;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.WORK_CACHE_NAME;

public class DefaultCacheEmbeddedConfigProviderFactory implements CacheEmbeddedConfigProviderFactory, CacheEmbeddedConfigProvider {

    private static final Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    // Map with the default cache configuration if the cache is not present in the XML.
    private static final Map<String, Supplier<ConfigurationBuilder>> DEFAULT_CONFIGS = Map.of(
            CRL_CACHE_NAME, InfinispanUtil::getCrlCacheConfig
    );
    private static final Supplier<ConfigurationBuilder> TO_NULL = () -> null;

    // Configuration
    private static final String CONFIG = "config";
    private static final String METRICS = "metricsEnabled";
    private static final String HISTOGRAMS = "histogramsEnabled";
    private static final String MAX_COUNT_SUFFIX = "MaxCount";

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
    public Optional<ConfigurationBuilderHolder> configuration() {
        return Optional.ofNullable(builderHolder);
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
        var builder = ProviderConfigurationBuilder.create();
        copyFromOption(builder, CONFIG, "file", ProviderConfigProperty.STRING_TYPE, CachingOptions.CACHE_CONFIG_FILE);
        copyFromOption(builder, HISTOGRAMS, "enabled", ProviderConfigProperty.BOOLEAN_TYPE, CachingOptions.CACHE_METRICS_HISTOGRAMS_ENABLED);
        copyFromOption(builder, METRICS, "enabled", ProviderConfigProperty.BOOLEAN_TYPE, MetricsOptions.METRICS_ENABLED);
        Stream.concat(
                Arrays.stream(LOCAL_CACHE_NAMES),
                Arrays.stream(CLUSTERED_MAX_COUNT_CACHES)
        ).forEach(name -> copyFromOption(builder, maxCountConfigKey(name), "max-count", ProviderConfigProperty.INTEGER_TYPE, CachingOptions.maxCountOption(name)));
        return builder.build();
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
            try {
                builderHolder = createConfiguration(factory);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected ConfigurationBuilderHolder createConfiguration(KeycloakSessionFactory factory) throws IOException {
        var holder = parseConfiguration(keycloakConfig);
        if (holder == null) {
            //no configuration file set
            return null;
        }
        if (InfinispanUtils.isRemoteInfinispan()) {
            return createMultiSiteConfiguration(holder, keycloakConfig);
        }
        if (Profile.isFeatureEnabled(Profile.Feature.PERSISTENT_USER_SESSIONS)) {
            return createPersistentUserSessionsConfiguration(holder, keycloakConfig, factory);
        }
        return createVolatileUserSessionsConfiguration(holder, keycloakConfig, factory);
    }

    private static ConfigurationBuilderHolder createVolatileUserSessionsConfiguration(ConfigurationBuilderHolder holder, Config.Scope keycloakConfig, KeycloakSessionFactory factory) {
        singleSiteConfiguration(keycloakConfig, holder);
        return holder;
    }

    private static ConfigurationBuilderHolder createPersistentUserSessionsConfiguration(ConfigurationBuilderHolder holder, Config.Scope keycloakConfig, KeycloakSessionFactory factory) {
        singleSiteConfiguration(keycloakConfig, holder);
        configureCacheMaxCount(keycloakConfig, holder, Arrays.stream(CLUSTERED_MAX_COUNT_CACHES));
        configureSessionsCachesForPersistentSessions(holder);
        return holder;
    }

    private static ConfigurationBuilderHolder createMultiSiteConfiguration(ConfigurationBuilderHolder holder, Config.Scope keycloakConfig) {
        removeClusteredCaches(holder);
        checkCachesExist(holder, Arrays.stream(LOCAL_CACHE_NAMES));
        configureMetrics(keycloakConfig, holder);
        // Disable JGroups, not required when the data is stored in the Remote Cache.
        // The existing caches are local and do not require JGroups to work properly.
        holder.getGlobalConfigurationBuilder().nonClusteredDefault();
        return holder;
    }

    private static ConfigurationBuilderHolder parseConfiguration(Config.Scope keycloakConfig) throws IOException {
        var config = keycloakConfig.get(CONFIG);
        if (config == null) {
            return null;
        }
        var holder = new ParserRegistry().parseFile(config);
        // We must disable the Infinispan default ShutdownHook as we manage the EmbeddedCacheManager lifecycle explicitly
        // with #shutdown and multiple calls to EmbeddedCacheManager#stop can lead to Exceptions being thrown
        holder.getGlobalConfigurationBuilder().shutdown().hookBehavior(ShutdownHookBehavior.DONT_REGISTER);
        Marshalling.configure(holder.getGlobalConfigurationBuilder());
        configureLocalCaches(keycloakConfig, holder);
        return holder;
    }

    private static void singleSiteConfiguration(Config.Scope config, ConfigurationBuilderHolder holder) {
        configureMetrics(config, holder);
        checkCachesExist(holder, Arrays.stream(ALL_CACHES_NAME));
        validateWorkCacheConfiguration(holder);
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
        for (var it = caches.iterator(); it.hasNext(); ) {
            var name = it.next();
            var builder = holder.getNamedConfigurationBuilders().get(name);
            if (builder == null) {
                throw cacheNotFound(name);
            }
            setMemoryMaxCount(keycloakConfig, name, builder);
        }
    }

    private static void configureMetrics(Config.Scope keycloakConfig, ConfigurationBuilderHolder holder) {
        if (keycloakConfig.getBoolean(METRICS, Boolean.FALSE)) {
            var histograms = keycloakConfig.getBoolean(HISTOGRAMS, Boolean.FALSE);
            var builder = holder.getGlobalConfigurationBuilder();
            builder.addModule(MicrometerMeterRegisterConfigurationBuilder.class)
                    .meterRegistry(Metrics.globalRegistry);
            builder.cacheContainer().statistics(true);
            builder.metrics()
                    .namesAsTags(true)
                    .histograms(histograms);
            holder.getNamedConfigurationBuilders()
                    .values()
                    .stream()
                    .map(ConfigurationBuilder::statistics)
                    .forEach(StatisticsConfigurationBuilder::enable);
        }
    }

    private static void checkCachesExist(ConfigurationBuilderHolder holder, Stream<String> caches) {
        for (var it = caches.iterator() ; it.hasNext() ; ) {
            var cache = it.next();
            var builder = holder.getNamedConfigurationBuilders().get(cache);
            if (builder == null) {
                throw cacheNotFound(cache);
            }
        }
    }

    private static void configureLocalCaches(Config.Scope keycloakConfig, ConfigurationBuilderHolder holder) {
        // configure local caches except revision caches
        for (var name : LOCAL_MAX_COUNT_CACHES) {
            var builder = holder.getNamedConfigurationBuilders().get(name);
            if (builder == null) {
                builder = DEFAULT_CONFIGS.getOrDefault(name, TO_NULL).get();
            }
            if (builder == null) {
                throw cacheNotFound(name);
            }
            setMemoryMaxCount(keycloakConfig, name, builder);
            holder.getNamedConfigurationBuilders().put(name, builder);
        }
        // configure revision caches
        configureRevisionCache(holder, REALM_CACHE_NAME, REALM_REVISIONS_CACHE_NAME, REALM_REVISIONS_CACHE_DEFAULT_MAX);
        configureRevisionCache(holder, USER_CACHE_NAME, USER_REVISIONS_CACHE_NAME, USER_REVISIONS_CACHE_DEFAULT_MAX);
        configureRevisionCache(holder, AUTHORIZATION_CACHE_NAME, AUTHORIZATION_REVISIONS_CACHE_NAME, AUTHORIZATION_REVISIONS_CACHE_DEFAULT_MAX);
        // check all caches are defined
        checkCachesExist(holder, Arrays.stream(LOCAL_CACHE_NAMES));
    }

    private static void setMemoryMaxCount(Config.Scope keycloakConfig, String name, ConfigurationBuilder builder) {
        var maxCount = keycloakConfig.getInt(maxCountConfigKey(name));
        if (maxCount != null) {
            builder.memory().maxCount(maxCount);
        }
    }

    private static IllegalStateException cacheNotFound(String cache) {
        return new IllegalStateException("Infinispan cache '%s' not found. Make sure it is defined in your XML configuration file.".formatted(cache));
    }

    private static void configureRevisionCache(ConfigurationBuilderHolder holder, String baseCache, String revisionCache, long defaultMaxEntries) {
        var maxCount = holder.getNamedConfigurationBuilders().get(baseCache).memory().maxCount();
        maxCount = maxCount > 0 ? 2 * maxCount : defaultMaxEntries;
        holder.getNamedConfigurationBuilders().put(revisionCache, InfinispanUtil.getRevisionCacheConfig(maxCount));
    }

    private static void configureSessionsCachesForPersistentSessions(ConfigurationBuilderHolder builder) {
        Stream.of(USER_SESSION_CACHE_NAME, CLIENT_SESSION_CACHE_NAME, OFFLINE_USER_SESSION_CACHE_NAME, OFFLINE_CLIENT_SESSION_CACHE_NAME)
                .forEach(cacheName -> {
                    var configurationBuilder = builder.getNamedConfigurationBuilders().get(cacheName);
                    if (MultiSiteUtils.isPersistentSessionsEnabled()) {
                        if (configurationBuilder.memory().maxCount() == -1) {
                            logger.infof("Persistent user sessions enabled and no memory limit found in configuration. Setting max entries for %s to 10000 entries.", cacheName);
                            configurationBuilder.memory().maxCount(10000);
                        }
                        /* The number of owners for these caches then need to be set to `1` to avoid backup owners with inconsistent data.
                         As primary owner evicts a key based on its locally evaluated maxCount setting, it wouldn't tell the backup owner about this, and then the backup owner would be left with a soon-to-be-outdated key.
                         While a `remove` is forwarded to the backup owner regardless if the key exists on the primary owner, a `computeIfPresent` is not, and it would leave a backup owner with an outdated key.
                         With the number of owners set to `1`, there will be no backup owners, so this is the setting to choose with persistent sessions enabled to ensure consistent data in the caches. */
                        configurationBuilder.clustering().hash().numOwners(1);
                    } else {
                        if (configurationBuilder.memory().maxCount() != -1) {
                            logger.warnf("Persistent user sessions disabled and memory limit found in configuration for cache %s. This might be a misconfiguration! Update your Infinispan configuration to remove this message.", cacheName);
                        }
                        if (configurationBuilder.memory().maxCount() == 10000 && (cacheName.equals(USER_SESSION_CACHE_NAME) || cacheName.equals(CLIENT_SESSION_CACHE_NAME))) {
                            logger.warnf("Persistent user sessions disabled and memory limit is set to default value 10000. Ignoring cache limits to avoid losing sessions for cache %s.", cacheName);
                            configurationBuilder.memory().maxCount(-1);
                        }
                        if (configurationBuilder.clustering().hash().attributes().attribute(HashConfiguration.NUM_OWNERS).get() == 1
                                && configurationBuilder.persistence().stores().isEmpty()) {
                            logger.warnf("Number of owners is one for cache %s, and no persistence is configured. This might be a misconfiguration as you will lose data when a single node is restarted!", cacheName);
                        }
                    }
                });
    }

    private static String maxCountConfigKey(String name) {
        return name + MAX_COUNT_SUFFIX;
    }

    private static void copyFromOption(ProviderConfigurationBuilder builder, String name, String label, String type, Option<?> option) {
        var property = builder.property()
                .name(name)
                .helpText(option.getDescription())
                .label(label)
                .type(type);
        option.getDefaultValue().ifPresent(property::defaultValue);
        property.add();
    }
}
