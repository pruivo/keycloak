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

package org.keycloak.quarkus.runtime.storage.infinispan.tls;

import org.jgroups.util.FileWatcher;
import org.jgroups.util.SocketFactory;
import org.jgroups.util.TLS;
import org.jgroups.util.TLSClientAuth;

public class CliJGroupsSocketFactory implements JGroupsSocketFactory {

    private final String keyStorePath;
    private final String keyStorePassword;
    private final String trustStorePath;
    private final String trustStorePassword;

    public CliJGroupsSocketFactory(String keyStorePath, String keyStorePassword, String trustStorePath, String trustStorePassword) {
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
        this.trustStorePath = trustStorePath;
        this.trustStorePassword = trustStorePassword;
    }

    @Override
    public SocketFactory create() {
        var tls = new TLS()
                .enabled(true)
                .setKeystorePath(keyStorePath)
                .setKeystorePassword(keyStorePassword)
                .setKeystoreType("pkcs12")
                .setTruststorePath(trustStorePath)
                .setTruststorePassword(trustStorePassword)
                .setTruststoreType("pkcs12")
                .setClientAuth(TLSClientAuth.NEED)
                .setProtocols(new String[]{"TLSv1.3"});
        tls.setWatcher(new FileWatcher());
        return tls.createSocketFactory();
    }
}
