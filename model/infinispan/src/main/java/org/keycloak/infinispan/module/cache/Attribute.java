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

import java.util.HashMap;
import java.util.Map;

public enum Attribute {
    // must be first
    UNKNOWN(null),
    OFFLINE_SESSIONS;

    private final String name;

    Attribute(final String name) {
        this.name = name;
    }

    Attribute() {
        this.name = name().toLowerCase().replace('_', '-');
    }

    /**
     * Get the local name of this element.
     *
     * @return the local name
     */
    public String getLocalName() {
        return name;
    }

    private static final Map<String, Attribute> attributes;

    static {
        Map<String, Attribute> map = new HashMap<>();
        for (Attribute attribute : values()) {
            final String name = attribute.getLocalName();
            if (name != null) {
                map.put(name, attribute);
            }
        }
        attributes = map;
    }

    public static Attribute forName(final String localName) {
        final Attribute attribute = attributes.get(localName);
        return attribute == null ? UNKNOWN : attribute;
    }

    @Override
    public String toString() {
        return name;
    }

}
