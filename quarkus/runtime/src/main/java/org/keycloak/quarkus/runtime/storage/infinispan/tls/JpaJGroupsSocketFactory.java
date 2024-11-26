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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

import org.jgroups.util.DefaultSocketFactory;
import org.jgroups.util.SocketFactory;
import org.keycloak.common.crypto.CryptoIntegration;
import org.keycloak.common.util.CertificateUtils;
import org.keycloak.common.util.KeyUtils;
import org.keycloak.common.util.KeystoreUtil;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.storage.certificate.CertificateDetail;
import org.keycloak.storage.certificate.CertificateStorageProvider;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;

public class JpaJGroupsSocketFactory implements JGroupsSocketFactory {

    private static final char[] KEY_PASSWORD = "jgroups-password".toCharArray();
    private static final String CERTIFICATE_ID = "jgroups";
    private static final String ALIAS = "jgroups";

    private final KeycloakSessionFactory sessionFactory;

    public JpaJGroupsSocketFactory(KeycloakSession session) {
        sessionFactory = session.getKeycloakSessionFactory();
    }

    @Override
    public SocketFactory create() {
        try (var session = sessionFactory.create()) {
            var storage = session.getProvider(CertificateStorageProvider.class);
            var data = storage.loadOrCreate(CERTIFICATE_ID, JpaJGroupsSocketFactory::generateSelfSignedCertificate);
            var km = createKeyManager(data.keyPair(), data.certificate());
            var tm = createTrustManager(data.certificate());
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(new KeyManager[]{km}, new TrustManager[]{tm}, null);
            return createFromContext(sslContext);
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private X509ExtendedKeyManager createKeyManager(KeyPair keyPair, X509Certificate certificate) throws GeneralSecurityException, IOException {
        var ks = CryptoIntegration.getProvider().getKeyStore(KeystoreUtil.KeystoreFormat.JKS);
        ks.load(null, null);
        ks.setKeyEntry(ALIAS, keyPair.getPrivate(), KEY_PASSWORD, new java.security.cert.Certificate[]{certificate});
        var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, KEY_PASSWORD);
        for (KeyManager km : kmf.getKeyManagers()) {
            if (km instanceof X509ExtendedKeyManager) {
                return (X509ExtendedKeyManager) km;
            }
        }
        throw new GeneralSecurityException("Could not obtain an X509ExtendedKeyManager");
    }

    private X509ExtendedTrustManager createTrustManager(X509Certificate certificate) throws GeneralSecurityException, IOException {
        var ks = CryptoIntegration.getProvider().getKeyStore(KeystoreUtil.KeystoreFormat.JKS);
        ks.load(null, null);
        ks.setCertificateEntry(ALIAS, certificate);
        var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509ExtendedTrustManager) {
                return (X509ExtendedTrustManager) tm;
            }
        }
        throw new GeneralSecurityException("Could not obtain an X509TrustManager");
    }

    private static CertificateDetail generateSelfSignedCertificate() {
        var keyPair = KeyUtils.generateRsaKeyPair(2048);
        var certificate = CertificateUtils.generateV1SelfSignedCertificate(keyPair, "jgroups");
        return new CertificateDetail(keyPair, certificate);
    }

    private static SocketFactory createFromContext(SSLContext context) {
        DefaultSocketFactory socketFactory = new DefaultSocketFactory(context);
        final SSLParameters serverParameters = new SSLParameters();
        serverParameters.setProtocols(new String[]{"TLSv1.3"});
        serverParameters.setNeedClientAuth(true);
        socketFactory.setServerSocketConfigurator(socket -> ((SSLServerSocket) socket).setSSLParameters(serverParameters));
        return socketFactory;
    }

}
