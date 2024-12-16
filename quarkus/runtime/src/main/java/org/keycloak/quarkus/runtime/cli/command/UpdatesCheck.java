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

import org.keycloak.quarkus.runtime.compatibility.ServerInfo;
import org.keycloak.util.JsonSerialization;
import picocli.CommandLine;

@CommandLine.Command(
        name = UpdatesCheck.NAME,
        description = "Does Stuff!")
public class UpdatesCheck extends AbstractUpdatesCommand {

    public static final String NAME = "check";

    @CommandLine.Option(names = {"--input-file"}, description = "Does stuff!")
    File inputFile;

    @Override
    public void run() {
        validateFile(inputFile);
        try {
            var info = JsonSerialization.mapper.readValue(inputFile, ServerInfo.class);
            var result = compatibilityManager.isCompatible(info);
            var cmd = getCommandLine();
            if (cmd.isPresent()) {
                var colorScheme = cmd.get().getColorScheme();
                var writer = cmd.get().getErr();
                result.errorMessage().map(colorScheme::errorText).ifPresent(writer::println);
            } else {
                result.errorMessage().ifPresent(System.err::println);
            }

            picocli.exit(result.exitCode());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return NAME;
    }
}
