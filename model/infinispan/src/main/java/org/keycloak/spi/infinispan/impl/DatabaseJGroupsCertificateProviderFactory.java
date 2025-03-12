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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.common.util.Retry;
import org.keycloak.infinispan.module.certificates.JGroups;
import org.keycloak.infinispan.module.certificates.JGroupsCertificate;
import org.keycloak.infinispan.module.certificates.Utils;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.spi.infinispan.JGroupsCertificateProvider;
import org.keycloak.spi.infinispan.JGroupsCertificateProviderFactory;
import org.keycloak.storage.configuration.ServerConfigStorageProvider;

import static org.keycloak.infinispan.module.certificates.JGroupsCertificate.fromJson;
import static org.keycloak.infinispan.module.certificates.JGroupsCertificate.toJson;
import static org.keycloak.spi.infinispan.impl.DatabaseJGroupsCertificateProvider.CERTIFICATE_ID;

public class DatabaseJGroupsCertificateProviderFactory implements JGroupsCertificateProviderFactory, EnvironmentDependentProviderFactory {

    private static final String PROVIDER_ID = "jpa";

    private static final String ROTATION_CONFIG_KEY = "rotation";
    private static final int ROTATION_DEFAULT_VALUE = 30;
    private static final Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    private static final int STARTUP_RETRIES = 5;
    private static final int STARTUP_RETRY_SLEEP_MILLIS = 500;

    private volatile Duration rotationPeriod;
    private volatile JGroups certificateHolder;

    @Override
    public JGroupsCertificateProvider create(KeycloakSession session) {
        if (certificateHolder == null) {
            postInit(session.getKeycloakSessionFactory());
        }
        return new DatabaseJGroupsCertificateProvider(certificateHolder, session, this::generateSelfSignedCertificate);
    }

    @Override
    public void init(Config.Scope config) {
        rotationPeriod = Duration.ofDays(config.getInt(ROTATION_CONFIG_KEY));
    }

    @Override
    public synchronized void postInit(KeycloakSessionFactory factory) {
        if (certificateHolder != null) {
            return;
        }
        logger.debug("Initializing JGroups mTLS certificate.");
        try {
            var cert = Retry.call(ignored -> KeycloakModelUtils.runJobInTransactionWithResult(factory, this::loadOrCreateCertificate), STARTUP_RETRIES, STARTUP_RETRY_SLEEP_MILLIS);
            var km = Utils.createKeyManager(cert);
            var tm = Utils.createTrustManager(null, cert);
            certificateHolder = new JGroups(cert, km, tm);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public Set<Class<? extends Provider>> dependsOn() {
        return Set.of(ServerConfigStorageProvider.class);
    }

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(ROTATION_CONFIG_KEY)
                .type("int")
                .label("days")
                .defaultValue(ROTATION_DEFAULT_VALUE)
                .add()
                .build();
    }

    private JGroupsCertificate loadOrCreateCertificate(KeycloakSession session) {
        var storage = session.getProvider(ServerConfigStorageProvider.class);
        return fromJson(storage.loadOrCreate(CERTIFICATE_ID, this::generateSelfSignedCertificate));
    }

    private String generateSelfSignedCertificate() {
        return toJson(Utils.generateSelfSignedCertificate(rotationPeriod.multipliedBy(2)));
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return Utils.isMtlsEnabled();
    }

    public void setRotationPeriod(Duration rotationPeriod) {
        this.rotationPeriod = Objects.requireNonNull(rotationPeriod);
    }

    public Duration getRotationPeriod() {
        return rotationPeriod;
    }

    public JGroupsCertificate currentCertificate() {
        return certificateHolder.getCertificate();
    }
}
