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

package org.keycloak.spi.infinispan;

import java.time.Duration;

import org.keycloak.provider.Provider;

import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;

public interface JGroupsCertificateProvider extends Provider {

    default void rotateCertificate() {
        throw new UnsupportedOperationException();
    }

    default void reloadCertificate() {
        throw new UnsupportedOperationException();
    }

    default Duration nextRotation() {
        throw new UnsupportedOperationException();
    }

    default boolean isEnabled() {
        return true;
    }

    default boolean supportsReloadAndRotation() {
        return false;
    }
}
