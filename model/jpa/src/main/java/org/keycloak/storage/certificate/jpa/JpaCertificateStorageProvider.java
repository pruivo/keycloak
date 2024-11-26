/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.storage.certificate.jpa;

import java.security.KeyPair;
import java.util.Objects;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.keycloak.common.util.PemUtils;
import org.keycloak.storage.certificate.CertificateDetail;
import org.keycloak.storage.certificate.CertificateStorageProvider;
import org.keycloak.storage.certificate.jpa.entity.CertificateDetailEntity;

public class JpaCertificateStorageProvider implements CertificateStorageProvider {

    private final EntityManager entityManager;

    public JpaCertificateStorageProvider(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public CertificateDetail load(String id) {
        var entity = entityManager.find(CertificateDetailEntity.class, Objects.requireNonNull(id));
        if (entity == null) {
            return null;
        }
        return entityToDetails(entity);
    }

    @Override
    public void store(String id, CertificateDetail certificateDetail) {
        var entity = detailsToEntity(id, Objects.requireNonNull(certificateDetail));
        entityManager.persist(entity);
    }

    @Override
    public void remove(String id) {
        var entity = entityManager.find(CertificateDetailEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    @Override
    public CertificateDetail loadOrCreate(String id, Supplier<CertificateDetail> generator) {
        var entity = entityManager.find(CertificateDetailEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (entity != null) {
            return entityToDetails(entity);
        }
        var details = generator.get();
        entityManager.persist(detailsToEntity(id, details));
        return details;
    }

    private static CertificateDetail entityToDetails(CertificateDetailEntity entity) {
        var privateKey = PemUtils.decodePrivateKey(entity.getPrivateKeyPem());
        var publicKey = PemUtils.decodePublicKey(entity.getPublicKeyPem());
        var certificate = PemUtils.decodeCertificate(entity.getCertificatePem());
        return new CertificateDetail(new KeyPair(publicKey, privateKey), certificate);
    }

    private static CertificateDetailEntity detailsToEntity(String id, CertificateDetail detail) {
        var privateKey = PemUtils.encodeKey(detail.keyPair().getPrivate());
        var publicKey = PemUtils.encodeKey(detail.keyPair().getPublic());
        var certificate = PemUtils.encodeCertificate(detail.certificate());
        return new CertificateDetailEntity(id, privateKey, publicKey, certificate);
    }
}
