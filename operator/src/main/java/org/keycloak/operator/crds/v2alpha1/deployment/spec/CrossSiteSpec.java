package org.keycloak.operator.crds.v2alpha1.deployment.spec;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.sundr.builder.annotations.Buildable;

import java.util.List;

@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
public class CrossSiteSpec {

    @JsonPropertyDescription("Local site name")
    private String site;

    @JsonPropertyDescription("List of Gossip Router hostname in the format host[port]. Example localhost[7900]")
    private List<String> gossipRouterHostnames;

    @JsonPropertyDescription("TLS spec")
    private CrossSiteTLSSpec tls;

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public List<String> getGossipRouterHostnames() {
        return gossipRouterHostnames;
    }

    public void setGossipRouterHostnames(List<String> gossipRouterHostnames) {
        this.gossipRouterHostnames = gossipRouterHostnames;
    }

    public CrossSiteTLSSpec getTls() {
        return tls;
    }

    public void setTls(CrossSiteTLSSpec tls) {
        this.tls = tls;
    }
}
