package org.keycloak.operator.crds.v2alpha1.deployment.spec;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.sundr.builder.annotations.Buildable;

@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class CrossSiteTruststoreSpec {

    @JsonPropertyDescription("The truststore file name present in the secret. Default is truststore.p12.")
    private String filename = "truststore.p12";

    @JsonPropertyDescription("The truststore type. Default is pkcs12.")
    private String type = "pkcs12";

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
}
