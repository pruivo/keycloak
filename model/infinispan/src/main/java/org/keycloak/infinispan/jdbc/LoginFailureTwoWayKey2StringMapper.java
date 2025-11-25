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

package org.keycloak.infinispan.jdbc;

import org.infinispan.persistence.keymappers.TwoWayKey2StringMapper;

import org.keycloak.models.sessions.infinispan.entities.LoginFailureKey;

public class LoginFailureTwoWayKey2StringMapper implements TwoWayKey2StringMapper {

    private static final char SEPARATOR = ':';

    @Override
    public LoginFailureKey getKeyMapping(String stringKey) {
        int idx = stringKey.indexOf(SEPARATOR);
        if (idx < 0) {
            return null;
        }
        return new LoginFailureKey(stringKey.substring(0, idx), stringKey.substring(idx + 1));
    }

    @Override
    public boolean isSupportedType(Class<?> keyType) {
        return keyType == LoginFailureKey.class;
    }

    @Override
    public String getStringMapping(Object key) {
        assert key instanceof LoginFailureKey;
        LoginFailureKey loginFailureKey = (LoginFailureKey) key;
        return loginFailureKey.realmId() + SEPARATOR + loginFailureKey.userId();
    }
}
