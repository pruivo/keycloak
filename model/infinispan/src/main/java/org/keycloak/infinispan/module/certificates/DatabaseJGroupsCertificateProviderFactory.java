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

package org.keycloak.infinispan.module.certificates;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.common.util.Retry;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.spi.infinispan.JGroupsCertificateProvider;
import org.keycloak.spi.infinispan.JGroupsCertificateProviderFactory;
import org.keycloak.spi.infinispan.JGroupsCertificateProviderSpi;
import org.keycloak.spi.infinispan.impl.FileJGroupsCertificateProviderFactory;
import org.keycloak.storage.configuration.ServerConfigStorageProvider;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import static org.keycloak.infinispan.module.certificates.DatabaseJGroupsCertificateProvider.CERTIFICATE_ID;
import static org.keycloak.infinispan.module.certificates.JGroupsCertificate.fromJson;
import static org.keycloak.infinispan.module.certificates.JGroupsCertificate.toJson;

public class DatabaseJGroupsCertificateProviderFactory implements JGroupsCertificateProviderFactory, EnvironmentDependentProviderFactory, DatabaseJGroupsCertificateProvider.CertificateStore {

    private static final String PROVIDER_ID = "jpa";

    private static final String ROTATION_CONFIG_KEY = "rotation";
    private static final int ROTATION_DEFAULT_VALUE = 30;
    private static final Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    private static final int STARTUP_RETRIES = 5;
    private static final int STARTUP_RETRY_SLEEP_MILLIS = 500;

    private final Lock lock = new ReentrantLock();
    private volatile Duration rotationPeriod;
    private volatile ReloadingX509ExtendedKeyManager keyManager;
    private volatile ReloadingX509ExtendedTrustManager trustManager;
    private volatile JGroupsCertificate currentCertificate;

    @Override
    public JGroupsCertificateProvider create(KeycloakSession session) {
        return new DatabaseJGroupsCertificateProvider(this, session);
    }

    @Override
    public void init(Config.Scope config) {
        rotationPeriod = Duration.ofDays(config.getInt(ROTATION_CONFIG_KEY));
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        lock.lock();
        try {
            if (currentCertificate != null) {
                return;
            }
            logger.debug("Initializing JGroups mTLS certificate.");

            var cert = Retry.call(ignored -> KeycloakModelUtils.runJobInTransactionWithResult(factory, this::loadOrCreateCertificate), STARTUP_RETRIES, STARTUP_RETRY_SLEEP_MILLIS);
            var km = Utils.createKeyManager(cert);
            var tm = Utils.createTrustManager(null, cert);
            this.currentCertificate = cert;
            this.keyManager = new ReloadingX509ExtendedKeyManager(km);
            this.trustManager = new ReloadingX509ExtendedTrustManager(tm);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
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
                .helpText("")
                .add()
                .build();
    }

    private JGroupsCertificate loadOrCreateCertificate(KeycloakSession session) {
        var storage = session.getProvider(ServerConfigStorageProvider.class);
        return fromJson(storage.loadOrCreate(CERTIFICATE_ID, this::generateSelfSignedCertificate));
    }

    private String generateSelfSignedCertificate() {
        return toJson(generate());
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        var fileConfig = Config.scope(JGroupsCertificateProviderSpi.SPI_NAME, FileJGroupsCertificateProviderFactory.PROVIDER_ID);
        return Utils.isMtlsEnabled() && !FileJGroupsCertificateProviderFactory.isFileMtlsEnabled(fileConfig);
    }

    public void setRotationPeriod(Duration rotationPeriod) {
        this.rotationPeriod = Objects.requireNonNull(rotationPeriod);
    }

    public Duration getRotationPeriod() {
        return rotationPeriod;
    }

    @Override
    public KeyManager keyManager() {
        return keyManager;
    }

    @Override
    public TrustManager trustManager() {
        return trustManager;
    }

    @Override
    public JGroupsCertificate certificate() {
        return currentCertificate;
    }

    @Override
    public boolean supportsReloadAndRotation() {
        return true;
    }

    @Override
    public void useCertificate(JGroupsCertificate certificate) {
        lock.lock();
        try {
            if (Objects.equals(currentCertificate.getAlias(), certificate.getAlias())) {
                return;
            }
            var km = Utils.createKeyManager(certificate);
            var tm = Utils.createTrustManager(currentCertificate, certificate);
            currentCertificate = certificate;
            keyManager.reload(km);
            trustManager.reload(tm);
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public JGroupsCertificate generate() {
        return Utils.generateSelfSignedCertificate(rotationPeriod.multipliedBy(2));
    }
}
