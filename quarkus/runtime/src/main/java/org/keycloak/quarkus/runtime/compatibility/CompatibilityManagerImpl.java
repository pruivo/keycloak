/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.quarkus.runtime.compatibility;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.keycloak.common.Version;

public class CompatibilityManagerImpl implements CompatibilityManager {

    private static final int EPOCH = 0;

    private final Map<String, CompatibilityComparator> versionComparators = new HashMap<>();

    @Override
    public ServerInfo current() {
        var info = new ServerInfo();
        info.setEpoch(EPOCH);
        addVersions(info);
        return info;
    }

    @Override
    public CompatibilityManager addVersionComparator(String versionKey, CompatibilityComparator comparator) {
        if (comparator != null) {
            versionComparators.put(versionKey, comparator);
        }
        return this;
    }

    private static void addVersions(ServerInfo info) {
        info.addVersion("keycloak", Version.VERSION);
        info.addVersion("infinispan", org.infinispan.commons.util.Version.getVersion());
    }

    @Override
    public CompatibilityResult isCompatible(ServerInfo other) {
        var current = current();
        if (current.getEpoch() != other.getEpoch()) {
            return new IncompatibleResult("Epoch", "Epoch", Integer.toString(current.getEpoch()), Integer.toString(other.getEpoch()));
        }
        return compareVersions(current, other);
    }

    private CompatibilityResult compareVersions(ServerInfo current, ServerInfo other) {
        var keys = Stream.concat(
                current.getVersions().keySet().stream(),
                other.getVersions().keySet().stream()
        ).collect(Collectors.toSet());
        for (var key : keys) {
            var comparator = versionComparators.getOrDefault(key, CompatibilityComparator.EQUALITY);
            var oldVersion = other.getVersions().get(key);
            var newVersion = current.getVersions().get(key);
            if (comparator.isCompatible(key, oldVersion, newVersion)) {
                continue;
            }
            return new IncompatibleResult("Versions", key, oldVersion, newVersion);
        }
        return CompatibilityResult.OK;
    }
}