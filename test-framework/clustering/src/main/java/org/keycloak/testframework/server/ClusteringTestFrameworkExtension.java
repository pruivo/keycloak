package org.keycloak.testframework.server;

import org.keycloak.testframework.TestFrameworkExtension;
import org.keycloak.testframework.injection.Supplier;

import java.util.List;

public class ClusteringTestFrameworkExtension implements TestFrameworkExtension {

    @Override
    public List<Supplier<?, ?>> suppliers() {
        return List.of(new ClusteredKeycloakServerSupplier());
    }
}
