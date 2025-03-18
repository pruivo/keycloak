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

package org.keycloak.spi.infinispan.impl;

import java.util.List;

import org.jgroups.util.FileWatcher;
import org.jgroups.util.SslContextFactory;
import org.keycloak.Config;
import org.keycloak.infinispan.module.certificates.Utils;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.spi.infinispan.JGroupsCertificateProvider;
import org.keycloak.spi.infinispan.JGroupsCertificateProviderFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

public class FileJGroupsCertificateProviderFactory implements JGroupsCertificateProviderFactory, EnvironmentDependentProviderFactory, JGroupsCertificateProvider {

    public static final String PROVIDER_ID = "file";

    public static final String KEYSTORE_PATH = "keystoreFile";
    private static final String KEYSTORE_PASSWORD = "keystorePassword";
    public static final String TRUSTSTORE_PATH = "truststoreFile";
    private static final String TRUSTSTORE_PASSWORD = "truststorePassword";

    private volatile SslContextFactory.Context context;

    @Override
    public boolean isSupported(Config.Scope config) {
        return Utils.isMtlsEnabled() && isFileMtlsEnabled(config);
    }

    @Override
    public KeyManager keyManager() {
        return context.keyManager();
    }

    @Override
    public TrustManager trustManager() {
        return context.trustManager();
    }

    @Override
    public JGroupsCertificateProvider create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(Config.Scope config) {
        context = new SslContextFactory()
                .sslProtocol("TLS")
                .keyStoreFileName(requireProperty(config, KEYSTORE_PATH))
                .keyStorePassword(requireProperty(config, KEYSTORE_PASSWORD))
                .keyStoreType("pkcs12")
                .trustStoreFileName(requireProperty(config, TRUSTSTORE_PATH))
                .trustStorePassword(requireProperty(config, KEYSTORE_PASSWORD))
                .trustStoreType("pkcs12")
                .watcher(new FileWatcher())
                .build();
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        //no-op
    }

    @Override
    public void close() {
        //no-op
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(KEYSTORE_PATH)
                .type(ProviderConfigProperty.STRING_TYPE)
                .label("file")
                .helpText("")
                .add()
                .property()
                .name(TRUSTSTORE_PATH)
                .type(ProviderConfigProperty.STRING_TYPE)
                .label("file")
                .helpText("")
                .add()
                .property()
                .name(KEYSTORE_PASSWORD)
                .type(ProviderConfigProperty.STRING_TYPE)
                .label("password")
                .helpText("")
                .secret(true)
                .add()
                .property()
                .name(TRUSTSTORE_PASSWORD)
                .type(ProviderConfigProperty.STRING_TYPE)
                .label("password")
                .helpText("")
                .secret(true)
                .add()
                .build();
    }

    public static boolean isFileMtlsEnabled(Config.Scope config) {
        return config.get(KEYSTORE_PATH) != null || config.get(TRUSTSTORE_PATH) != null;
    }

    private static String requireProperty(Config.Scope config, String key) {
        var value = config.get(key);
        if (value == null) {
            throw new RuntimeException("Property " + key + " required but not specified");
        }
        return value;
    }
}
