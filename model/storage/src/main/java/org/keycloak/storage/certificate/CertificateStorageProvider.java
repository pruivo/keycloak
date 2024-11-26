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

package org.keycloak.storage.certificate;

import java.util.function.Supplier;

import org.keycloak.provider.Provider;

public interface CertificateStorageProvider extends Provider {

    CertificateDetail load(String id);

    void store(String id, CertificateDetail certificateDetail);

    void remove(String id);

    default CertificateDetail loadOrCreate(String id, Supplier<CertificateDetail> generator) {
        var details = load(id);
        if (details == null) {
            details = generator.get();
            store(id, details);
        }
        return details;
    }

    @Override
    default void close() {

    }
}
