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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.keycloak.common.util.Time;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.spi.infinispan.JGroupsCertificateProvider;
import org.keycloak.storage.configuration.ServerConfigStorageProvider;

public class DatabaseJGroupsCertificateProvider implements JGroupsCertificateProvider, Supplier<String> {

    public static final String CERTIFICATE_ID = "crt_jgroups";
    private final CertificateStore store;
    private final KeycloakSessionFactory sessionFactory;

    public DatabaseJGroupsCertificateProvider(CertificateStore certificateStore, KeycloakSession session) {
        this.store = Objects.requireNonNull(certificateStore);
        this.sessionFactory = session.getKeycloakSessionFactory();
    }

    @Override
    public void close() {

    }

    @Override
    public void rotateCertificate() {
        KeycloakModelUtils.runJobInTransaction(sessionFactory, this::replaceCertificateFromDatabase);
    }

    @Override
    public void reloadCertificate() {
        doReload();
    }

    @Override
    public Duration nextRotation() {
        var cert = store.certificate().getCertificate();
        return delayUntilNextRotation(cert.getNotAfter().toInstant(), cert.getNotAfter().toInstant());
    }

    @Override
    public String get() {
        // Supplier interface to generate a new certificate
        return JGroupsCertificate.toJson(store.generate());
    }

    void onTrustManagerException() {
        doReload();
    }

    private void doReload() {
        KeycloakModelUtils.runJobInTransactionWithResult(sessionFactory, DatabaseJGroupsCertificateProvider::loadCertificateFromDatabase)
                .map(JGroupsCertificate::fromJson)
                .ifPresent(store::useCertificate);
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
        var holder = store.certificate();
        storage.replace(CERTIFICATE_ID, holder::isSameAlias, this);
    }

    public interface CertificateStore {
        JGroupsCertificate certificate();

        void useCertificate(JGroupsCertificate certificate);

        JGroupsCertificate generate();
    }
}
