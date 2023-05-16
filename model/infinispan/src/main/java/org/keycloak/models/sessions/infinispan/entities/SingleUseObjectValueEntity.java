/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.models.sessions.infinispan.entities;

import org.infinispan.commons.marshall.MarshallUtil;
import org.keycloak.models.SingleUseObjectValueModel;

import java.io.*;
import java.util.*;
import org.infinispan.commons.marshall.Externalizer;
import org.infinispan.commons.marshall.SerializeWith;
import org.keycloak.models.sessions.infinispan.util.KeycloakMarshallUtil;

/**
 * @author hmlnarik
 */
@SerializeWith(SingleUseObjectValueEntity.ExternalizerImpl.class)
public class SingleUseObjectValueEntity implements SingleUseObjectValueModel {

    private final Map<String, String> notes;

    public SingleUseObjectValueEntity(Map<String, String> notes) {
        this.notes = notes == null ? Collections.EMPTY_MAP : new HashMap<>(notes);
    }

    @Override
    public Map<String, String> getNotes() {
        return Collections.unmodifiableMap(notes);
    }

    @Override
    public String getNote(String name) {
        return notes.get(name);
    }

    @Override
    public String toString() {
        return String.format("SingleUseObjectValueEntity [ notes=%s ]", notes);
    }

    public static class ExternalizerImpl implements Externalizer<SingleUseObjectValueEntity> {

        private static final int VERSION_1 = 1;
        private static final int VERSION_2 = 2;

        @Override
        public void writeObject(ObjectOutput output, SingleUseObjectValueEntity t) throws IOException {
            output.writeByte(VERSION_2);
            MarshallUtil.marshallMap(t.notes, KeycloakMarshallUtil.STRING_WRITER, KeycloakMarshallUtil.STRING_WRITER, output);
        }

        @Override
        public SingleUseObjectValueEntity readObject(ObjectInput input) throws IOException, ClassNotFoundException {
            switch (input.readByte()) {
                case VERSION_1:
                    return readObjectVersion1(input);
                case VERSION_2:
                    return readObjectVersion2(input);
                default:
                    throw new IOException("Unknown version");
            }
        }

        private SingleUseObjectValueEntity readObjectVersion1(ObjectInput input) throws IOException, ClassNotFoundException {
            Map<String, String> notes = input.readBoolean() ?
                    Collections.emptyMap() :
                    (Map<String, String>) input.readObject();
            return new SingleUseObjectValueEntity(notes);
        }

        private SingleUseObjectValueEntity readObjectVersion2(ObjectInput input) throws IOException, ClassNotFoundException {
            Map<String, String> notes = MarshallUtil.unmarshallMap(input, MarshallUtil::unmarshallString, MarshallUtil::unmarshallString, HashMap::new);
            assert notes != null;
            return new SingleUseObjectValueEntity(notes.isEmpty() ? Collections.emptyMap() : notes);
        }
    }
}
