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

package org.keycloak.quarkus.runtime.cli.command;

import java.io.File;
import java.io.IOException;

import org.keycloak.util.JsonSerialization;
import picocli.CommandLine;

@CommandLine.Command(
        name = UpdatesSave.NAME,
        description = "Does Stuff!")
public class UpdatesSave extends AbstractUpdatesCommand {

    public static final String NAME = "save";

    @CommandLine.Option(names = {"--output-file"}, description = "Does stuff!")
    File outputFile;

    @Override
    public void run() {
        validateFile(outputFile);
        var info = compatibilityManager.current();

        try {
            System.out.println(JsonSerialization.mapper.writeValueAsString(info));
            if (outputFile != null) {
                JsonSerialization.mapper.writeValue(outputFile, info);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return NAME;
    }
}
