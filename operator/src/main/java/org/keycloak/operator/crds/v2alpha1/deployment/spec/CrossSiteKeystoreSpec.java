package org.keycloak.operator.crds.v2alpha1.deployment.spec;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.sundr.builder.annotations.Buildable;

@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class CrossSiteKeystoreSpec {

    @JsonPropertyDescription("The keystore file name present in the secret. Default is keystore.p12.")
    private String filename = "keystore.p12";

    @JsonPropertyDescription("The keystore type. Default is pkcs12.")
    private String type = "pkcs12";

    @JsonPropertyDescription("The certificate alias present in the keystore. Default is 'server'.")
    private String alias = "server";

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }
}
