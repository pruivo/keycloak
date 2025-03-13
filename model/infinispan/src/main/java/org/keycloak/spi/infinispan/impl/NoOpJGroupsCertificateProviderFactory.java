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

import org.keycloak.Config;
import org.keycloak.infinispan.module.certificates.Utils;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.spi.infinispan.JGroupsCertificateProvider;
import org.keycloak.spi.infinispan.JGroupsCertificateProviderFactory;

import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;

public class NoOpJGroupsCertificateProviderFactory implements JGroupsCertificateProviderFactory, JGroupsCertificateProvider, EnvironmentDependentProviderFactory {

    @Override
    public JGroupsCertificateProvider create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return "empty";
    }

    @Override
    public X509ExtendedKeyManager keyManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public X509ExtendedTrustManager trustManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return !Utils.isMtlsEnabled();
    }
}
