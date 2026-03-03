package org.keycloak.quarkus.runtime.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;

import org.keycloak.quarkus.runtime.integration.jaxrs.QuarkusKeycloakApplication;

import org.jboss.resteasy.reactive.server.ServerRequestFilter;

@ApplicationScoped
public class BootstrapFilter {

    @ServerRequestFilter(priority = 1)
    public Response filter(ContainerRequestContext requestContext) {
        if (QuarkusKeycloakApplication.isBootstrapCompleted()) {
            // Return null to continue the request chain normally
            return null;
        }
        // Return 503 Service Unavailable
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity("Keycloak is initializing...")
                .build();

    }
}
