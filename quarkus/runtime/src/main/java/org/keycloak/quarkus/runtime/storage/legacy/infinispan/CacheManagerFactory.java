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

package org.keycloak.quarkus.runtime.storage.legacy.infinispan;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Metrics;
import org.infinispan.commons.util.SslContextFactory;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.jboss.marshalling.core.JBossUserMarshaller;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.metrics.config.MicrometerMeterRegisterConfigurationBuilder;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.infinispan.remoting.transport.jgroups.NamedSocketFactory;
import org.jboss.logging.Logger;
import org.keycloak.quarkus.runtime.configuration.Configuration;

import javax.net.ssl.SSLContext;

import static org.keycloak.config.CachingOptions.JGROUPS_TLS_ENABLED_PROPERTY;
import static org.keycloak.config.CachingOptions.JGROUPS_TLS_KEYSTORE_ALIAS_PROPERTY;
import static org.keycloak.config.CachingOptions.JGROUPS_TLS_KEYSTORE_FILE_PROPERTY;
import static org.keycloak.config.CachingOptions.JGROUPS_TLS_KEYSTORE_PASSWORD_PROPERTY;
import static org.keycloak.config.CachingOptions.JGROUPS_TLS_KEYSTORE_TYPE_PROPERTY;
import static org.keycloak.config.CachingOptions.JGROUPS_TLS_PROTOCOL_PROPERTY;
import static org.keycloak.config.CachingOptions.JGROUPS_TLS_PROVIDER_PROPERTY;
import static org.keycloak.config.CachingOptions.JGROUPS_TLS_TRUSTSTORE_FILE_PROPERTY;
import static org.keycloak.config.CachingOptions.JGROUPS_TLS_TRUSTSTORE_PASSWORD_PROPERTY;
import static org.keycloak.config.CachingOptions.JGROUPS_TLS_TRUSTSTORE_TYPE_PROPERTY;

public class CacheManagerFactory {

    private String config;
    private final boolean metricsEnabled;
    private DefaultCacheManager cacheManager;
    private Future<DefaultCacheManager> cacheManagerFuture;
    private ExecutorService executor;
    private boolean initialized;

    public CacheManagerFactory(String config, boolean metricsEnabled) {
        this.config = config;
        this.metricsEnabled = metricsEnabled;
        this.executor = createThreadPool();
        this.cacheManagerFuture = executor.submit(this::startCacheManager);
    }

    public DefaultCacheManager getOrCreate() {
        if (cacheManager == null) {
            if (initialized) {
                return null;
            }

            try {
                // for now, we don't have any explicit property for setting the cache start timeout
                return cacheManager = cacheManagerFuture.get(getStartTimeout(), TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException("Failed to start caches", e);
            } finally {
                shutdownThreadPool();
            }
        }

        return cacheManager;
    }

    private ExecutorService createThreadPool() {
        return Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "keycloak-cache-init");
            }
        });
    }

    private DefaultCacheManager startCacheManager() {
        ConfigurationBuilderHolder builder = new ParserRegistry().parse(config);

        if (builder.getNamedConfigurationBuilders().get("sessions").clustering().cacheMode().isClustered()) {
            configureTransportStack(builder);
        }

        if (metricsEnabled) {
            builder.getGlobalConfigurationBuilder().addModule(MicrometerMeterRegisterConfigurationBuilder.class);
            builder.getGlobalConfigurationBuilder().module(MicrometerMeterRegisterConfigurationBuilder.class).meterRegistry(Metrics.globalRegistry);
        }

        // For Infinispan 10, we go with the JBoss marshalling.
        // TODO: This should be replaced later with the marshalling recommended by infinispan. Probably protostream.
        // See https://infinispan.org/docs/stable/titles/developing/developing.html#marshalling for the details
        builder.getGlobalConfigurationBuilder().serialization().marshaller(new JBossUserMarshaller());

        return new DefaultCacheManager(builder, isStartEagerly());
    }

    private boolean isStartEagerly() {
        // eagerly starts caches by default
        return Boolean.parseBoolean(System.getProperty("kc.cache-ispn-start-eagerly", Boolean.TRUE.toString()));
    }

    private Integer getStartTimeout() {
        return Integer.getInteger("kc.cache-ispn-start-timeout", 120);
    }

    private void shutdownThreadPool() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                        Logger.getLogger(CacheManagerFactory.class).warn("Cache init thread pool not terminated");
                    }
                }
            } catch (Exception cause) {
                executor.shutdownNow();
            } finally {
                executor = null;
                cacheManagerFuture = null;
                config = null;
                initialized = true;
            }
        }
    }

    private void configureTransportStack(ConfigurationBuilderHolder builder) {
        String transportStack = Configuration.getRawValue("kc.cache-stack");

        if (transportStack != null && !transportStack.isBlank()) {
            builder.getGlobalConfigurationBuilder().transport().defaultTransport().stack(transportStack);
        }

        if (booleanProperty(JGROUPS_TLS_ENABLED_PROPERTY)) {
            SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory
                    .sslProtocol(stringProperty(JGROUPS_TLS_PROTOCOL_PROPERTY))
                    .provider(stringProperty(JGROUPS_TLS_PROVIDER_PROPERTY))
                    .keyStoreFileName(stringProperty(JGROUPS_TLS_KEYSTORE_FILE_PROPERTY))
                    .keyStorePassword(passwordProperty(JGROUPS_TLS_KEYSTORE_PASSWORD_PROPERTY))
                    .keyStoreType(stringProperty(JGROUPS_TLS_KEYSTORE_TYPE_PROPERTY))
                    .keyAlias(stringProperty(JGROUPS_TLS_KEYSTORE_ALIAS_PROPERTY))
                    .trustStoreFileName(stringProperty(JGROUPS_TLS_TRUSTSTORE_FILE_PROPERTY))
                    .trustStorePassword(passwordProperty(JGROUPS_TLS_TRUSTSTORE_PASSWORD_PROPERTY))
                    .trustStoreType(stringProperty(JGROUPS_TLS_TRUSTSTORE_TYPE_PROPERTY))
                    .useNativeIfAvailable(false) //requires wildfly-openssl
                    .classLoader(Thread.currentThread().getContextClassLoader());
            SSLContext context = sslContextFactory.getContext();
            NamedSocketFactory namedSocketFactory = new NamedSocketFactory(context::getSocketFactory, context::getServerSocketFactory);
            builder.getGlobalConfigurationBuilder().transport().addProperty(JGroupsTransport.SOCKET_FACTORY, namedSocketFactory);
        }
    }

    private static char[] passwordProperty(String propertyName) {
        return Configuration.getOptionalKcValue(propertyName).map(String::toCharArray).orElse(null);
    }

    private static boolean booleanProperty(String propertyName) {
        return Configuration.getOptionalKcValue(propertyName).map(Boolean::parseBoolean).orElse(Boolean.FALSE);
    }

    private static String stringProperty(String propertyName) {
        return Configuration.getOptionalKcValue(propertyName).orElse(null);
    }
}
