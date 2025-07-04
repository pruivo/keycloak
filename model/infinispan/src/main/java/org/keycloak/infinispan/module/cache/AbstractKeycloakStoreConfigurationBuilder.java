/*
 * Copyright 2025 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.infinispan.module.cache;

import org.infinispan.commons.configuration.attributes.AttributeSet;
import org.infinispan.configuration.cache.AbstractStoreConfigurationBuilder;
import org.infinispan.configuration.cache.PersistenceConfigurationBuilder;

abstract class AbstractKeycloakStoreConfigurationBuilder<C extends AbstractKeycloakStoreConfiguration<C>, B extends AbstractKeycloakStoreConfigurationBuilder<C, B>> extends AbstractStoreConfigurationBuilder<C, B> {

    public AbstractKeycloakStoreConfigurationBuilder(PersistenceConfigurationBuilder builder, AttributeSet attributes) {
        super(builder, attributes);
    }

    public B offlineSessions(boolean offlineSessions) {
        attributes.attribute(AbstractKeycloakStoreConfiguration.OFFLINE).set(offlineSessions);
        return self();
    }
}
