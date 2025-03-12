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

import java.util.Objects;

import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;

public class JGroups {

    private volatile JGroupsCertificate certificate;
    private final ReloadingX509ExtendedKeyManager keyManager;
    private final ReloadingX509ExtendedTrustManager trustManager;

    public JGroups(JGroupsCertificate certificate, X509ExtendedKeyManager keyManager, X509ExtendedTrustManager trustManager) {
        this.certificate = Objects.requireNonNull(certificate);
        this.keyManager = new ReloadingX509ExtendedKeyManager(keyManager);
        this.trustManager = new ReloadingX509ExtendedTrustManager(trustManager);
    }

    public JGroupsCertificate getCertificate() {
        return certificate;
    }

    public X509ExtendedKeyManager getKeyManager() {
        return keyManager;
    }

    public X509ExtendedTrustManager getTrustManager() {
        return trustManager;
    }

    public void updateCertificate(JGroupsCertificate certificate, X509ExtendedKeyManager keyManager, X509ExtendedTrustManager trustManager) {
        this.certificate = Objects.requireNonNull(certificate);
        this.keyManager.reload(keyManager);
        this.trustManager.reload(trustManager);
    }
}
