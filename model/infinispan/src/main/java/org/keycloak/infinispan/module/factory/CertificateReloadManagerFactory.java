package org.keycloak.infinispan.module.factory;

import org.infinispan.factories.AbstractComponentFactory;
import org.infinispan.factories.AutoInstantiableFactory;
import org.infinispan.factories.annotations.DefaultFactoryFor;
import org.keycloak.infinispan.module.certificates.CertificateReloadManager;
import org.keycloak.infinispan.module.configuration.global.KeycloakConfiguration;
import org.keycloak.spi.infinispan.JGroupsCertificateProvider;

@DefaultFactoryFor(classes = CertificateReloadManager.class)
public class CertificateReloadManagerFactory extends AbstractComponentFactory implements AutoInstantiableFactory {

    @Override
    public Object construct(String componentName) {
        var kcConfig = globalConfiguration.module(KeycloakConfiguration.class);
        if (kcConfig == null) {
            return null;
        }
        var sessionFactory = kcConfig.keycloakSessionFactory();
        try (var session = sessionFactory.create()) {
            var provider = session.getProvider(JGroupsCertificateProvider.class);
            if (provider.isEnabled() && provider.supportsReloadAndRotation()) {
                return new CertificateReloadManager(sessionFactory);
            }
        }
        return null;
    }
}
