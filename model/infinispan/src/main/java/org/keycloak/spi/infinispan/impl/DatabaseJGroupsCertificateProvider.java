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
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.keycloak.common.util.Time;
import org.keycloak.infinispan.module.certificates.JGroups;
import org.keycloak.infinispan.module.certificates.JGroupsCertificate;
import org.keycloak.infinispan.module.certificates.Utils;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.spi.infinispan.JGroupsCertificateProvider;
import org.keycloak.storage.configuration.ServerConfigStorageProvider;

import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;

public class DatabaseJGroupsCertificateProvider implements JGroupsCertificateProvider {

    public static final String CERTIFICATE_ID = "crt_jgroups";
    private final JGroups certificateHolder;
    private final KeycloakSession session;
    private final Supplier<String> certGenerator;

    public DatabaseJGroupsCertificateProvider(JGroups certificateHolder, KeycloakSession session, Supplier<String> certGenerator) {
        this.certificateHolder = certificateHolder;
        this.session = session;
        this.certGenerator = certGenerator;
    }

    @Override
    public void close() {

    }

    @Override
    public X509ExtendedKeyManager keyManager() {
        return certificateHolder.getKeyManager();
    }

    @Override
    public X509ExtendedTrustManager trustManager() {
        return certificateHolder.getTrustManager();
    }

    @Override
    public void rotateCertificate() {
        KeycloakModelUtils.runJobInTransaction(session.getKeycloakSessionFactory(), this::replaceCertificateFromDatabase);
    }

    @Override
    public void reloadCertificate() {
        doReload();
    }

    @Override
    public Duration nextRotation() {
        var cert = certificateHolder.getCertificate().getCertificate();
        return delayUntilNextRotation(cert.getNotAfter().toInstant(), cert.getNotAfter().toInstant());
    }

    @Override
    public boolean supportsReloadAndRotation() {
        return true;
    }

    void onTrustManagerException() {
        doReload();
    }

    private void doReload() {
        var maybeCert = KeycloakModelUtils.runJobInTransactionWithResult(session.getKeycloakSessionFactory(), DatabaseJGroupsCertificateProvider::loadCertificateFromDatabase);
        if (maybeCert.isEmpty()) {
            return;
        }
        var loadedCert = JGroupsCertificate.fromJson(maybeCert.get());
        var currentCert = certificateHolder.getCertificate();
        if (Objects.equals(loadedCert.getAlias(), currentCert.getAlias())) {
            return;
        }
        try {
            certificateHolder.updateCertificate(loadedCert,
                    Utils.createKeyManager(loadedCert),
                    Utils.createTrustManager(currentCert, loadedCert));
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Duration delayUntilNextRotation(Instant certificateStartInstant, Instant certificateEndInstant) {
        // Avoid the current certificate to expire if the old duration was shorter than the new duration.
        var rotationInstant = certificateStartInstant.plus(Duration.between(certificateStartInstant, certificateEndInstant).dividedBy(2));
        var secondsLeft = Instant.ofEpochSecond(Time.currentTime()).until(rotationInstant, ChronoUnit.SECONDS);
        return secondsLeft > 0 ? Duration.ofSeconds(secondsLeft) : Duration.ZERO;
    }

    private static Optional<String> loadCertificateFromDatabase(KeycloakSession session) {
        return session.getProvider(ServerConfigStorageProvider.class).find(CERTIFICATE_ID);
    }

    private void replaceCertificateFromDatabase(KeycloakSession session) {
        var storage = session.getProvider(ServerConfigStorageProvider.class);
        var holder = certificateHolder.getCertificate();
        storage.replace(CERTIFICATE_ID, holder::isSameAlias, certGenerator);
    }
}
