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

package org.keycloak.infinispan.module.configuration.cache;

import org.keycloak.infinispan.module.persistence.LoginFailuresStore;

import org.infinispan.commons.configuration.BuiltBy;
import org.infinispan.commons.configuration.ConfigurationFor;
import org.infinispan.commons.configuration.attributes.AttributeSet;
import org.infinispan.configuration.cache.AbstractStoreConfiguration;
import org.infinispan.configuration.cache.AsyncStoreConfiguration;
import org.infinispan.configuration.serializing.SerializedWith;

@ConfigurationFor(LoginFailuresStore.class)
@BuiltBy(LoginFailuresStoreConfigurationBuilder.class)
@SerializedWith(UserSessionKeycloakStoreConfigurationSerializer.class)
public class LoginFailuresStoreConfiguration extends AbstractStoreConfiguration<LoginFailuresStoreConfiguration> {

    protected LoginFailuresStoreConfiguration(AttributeSet attributes, AsyncStoreConfiguration async) {
        super(Element.LOGIN_FAILURES_STORE, attributes, async);
    }

    public static AttributeSet attributeDefinitionSet() {
        return new AttributeSet(LoginFailuresStoreConfiguration.class, AbstractStoreConfiguration.attributeDefinitionSet());
    }
}
