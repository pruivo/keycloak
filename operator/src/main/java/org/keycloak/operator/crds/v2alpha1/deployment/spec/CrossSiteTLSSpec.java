package org.keycloak.operator.crds.v2alpha1.deployment.spec;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.sundr.builder.annotations.Buildable;

@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class CrossSiteTLSSpec {

    @JsonPropertyDescription("Enables TLS traffic for cross-site communication")
    private boolean enabled;

    @JsonPropertyDescription("Secret name where the keystore and truststore (and their passwords) are")
    private String secretName;

    @JsonPropertyDescription("The TLS protocol to use. Defaults to TLSv1.3")
    private String protocol = "TLSv1.3";

    @JsonPropertyDescription("The Keystore configuration to use for TLS communication")
    private CrossSiteKeystoreSpec keystore = new CrossSiteKeystoreSpec();

    @JsonPropertyDescription("The Truststore configuration to use for TLS communication")
    private CrossSiteTruststoreSpec truststore = new CrossSiteTruststoreSpec();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecretName() {
        return secretName;
    }

    public void setSecretName(String secretName) {
        this.secretName = secretName;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public CrossSiteKeystoreSpec getKeystore() {
        return keystore;
    }

    public void setKeystore(CrossSiteKeystoreSpec keystore) {
        this.keystore = keystore;
    }

    public CrossSiteTruststoreSpec getTruststore() {
        return truststore;
    }

    public void setTruststore(CrossSiteTruststoreSpec truststore) {
        this.truststore = truststore;
    }
}
