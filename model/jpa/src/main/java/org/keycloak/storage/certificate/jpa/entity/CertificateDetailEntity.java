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

package org.keycloak.storage.certificate.jpa.entity;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Table(name = "CERTIFICATES")
@Entity
public class CertificateDetailEntity {

    @Id
    @Column(name = "ID")
    private final String id;

    @Column(name = "PRIVATE_KEY")
    private final String privateKeyPem;

    @Column(name = "PUBLIC_KEY")
    private final String publicKeyPem;

    @Column(name = "CERTIFICATE")
    private final String certificatePem;

    public CertificateDetailEntity(String id, String privateKeyPem, String publicKeyPem, String certificatePem) {
        this.id = Objects.requireNonNull(id);
        this.privateKeyPem = Objects.requireNonNull(privateKeyPem);
        this.publicKeyPem = Objects.requireNonNull(publicKeyPem);
        this.certificatePem = Objects.requireNonNull(certificatePem);
    }

    public String getCertificatePem() {
        return certificatePem;
    }

    public String getId() {
        return id;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        CertificateDetailEntity that = (CertificateDetailEntity) o;
        return id.equals(that.id) &&
                privateKeyPem.equals(that.privateKeyPem) &&
                publicKeyPem.equals(that.publicKeyPem) &&
                certificatePem.equals(that.certificatePem);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + privateKeyPem.hashCode();
        result = 31 * result + publicKeyPem.hashCode();
        result = 31 * result + certificatePem.hashCode();
        return result;
    }
}
