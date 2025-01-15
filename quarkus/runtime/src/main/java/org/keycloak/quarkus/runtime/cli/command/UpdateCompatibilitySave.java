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

import org.keycloak.quarkus.runtime.cli.PropertyException;
import org.keycloak.quarkus.runtime.compatibility.ServerInfo;
import org.keycloak.util.JsonSerialization;
import picocli.CommandLine;

@CommandLine.Command(
        name = UpdateCompatibilitySave.NAME,
        description = "Does Stuff!")
public class UpdateCompatibilitySave extends AbstractUpdatesCommand {

    public static final String NAME = "save";
    private static final String OUTPUT_OPTION_NAME = "--output";

    @CommandLine.Option(names = {OUTPUT_OPTION_NAME}, paramLabel = "FILE", description = "Does stuff!")
    String outputFile;

    @Override
    public void run() {
        validateOutputFile();
        var info = compatibilityManager.current();
        writeServerInfo(info);
        writeComplete("written");
    }

    @Override
    public String getName() {
        return NAME;
    }

    private void validateOutputFile() {
        validateOptionPresent(outputFile, OUTPUT_OPTION_NAME);
        var file = new File(outputFile);
        if (file.getParentFile() != null && !file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            throw new PropertyException("Incorrect argument %s. Unable to create parent directory: %s".formatted(OUTPUT_OPTION_NAME, file.getParentFile().getAbsolutePath()));
        }
        validateNotDirectory(file, OUTPUT_OPTION_NAME);
    }

    private void writeServerInfo(ServerInfo info) {
        var file = new File(outputFile);
        try {
            JsonSerialization.mapper.writeValue(file, info);
        } catch (IOException e) {
            throw new PropertyException("Unable to write file '%s'".formatted(file.getAbsolutePath()), e);
        }
    }
}
