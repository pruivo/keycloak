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

import org.infinispan.commons.configuration.io.ConfigurationReader;
import org.infinispan.configuration.cache.PersistenceConfigurationBuilder;
import org.infinispan.configuration.parsing.CacheParser;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ConfigurationParser;
import org.infinispan.configuration.parsing.Namespace;
import org.infinispan.configuration.parsing.ParseUtils;
import org.infinispan.configuration.parsing.Parser;
import org.kohsuke.MetaInfServices;

import static org.keycloak.infinispan.module.configuration.cache.KeycloakStoreConfigurationParser.NAMESPACE;


@MetaInfServices(ConfigurationParser.class)
@Namespace(root = "login-failures-store")
@Namespace(uri = NAMESPACE + "*", root = "login-failures-store")
public class KeycloakStoreConfigurationParser implements ConfigurationParser {

    static final String NAMESPACE = Parser.NAMESPACE + "store:keycloak:";

    @Override
    public void readElement(ConfigurationReader reader, ConfigurationBuilderHolder holder) {
        var cacheBuilder = holder.getCurrentConfigurationBuilder();
        var element = Element.forName(reader.getLocalName());
        //noinspection SwitchStatementWithTooFewBranches
        switch (element) {
            case LOGIN_FAILURES_STORE: {
                parseLoginFailureStore(reader, cacheBuilder.persistence());
                break;
            }
            default: {
                throw ParseUtils.unexpectedElement(reader);
            }
        }
    }

    private void parseLoginFailureStore(ConfigurationReader reader, PersistenceConfigurationBuilder persistence) {
        var builder = new LoginFailuresStoreConfigurationBuilder(persistence);
        ParseUtils.parseAttributes(reader, builder);
        while (reader.inTag()) {
            CacheParser.parseStoreElement(reader, builder);
        }
        persistence.addStore(builder);
    }

    @Override
    public Namespace[] getNamespaces() {
        return ParseUtils.getNamespaceAnnotations(getClass());
    }
}
