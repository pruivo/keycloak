package org.keycloak.config;

import java.io.File;

public class CachingOptions {

    private static final String JGRP_TLS = "jgroups-tls";
    public static final String JGROUPS_TLS_ENABLED_PROPERTY = JGRP_TLS + "-enabled";
    public static final String JGROUPS_TLS_PROTOCOL_PROPERTY = JGRP_TLS + "-protocol";
    public static final String JGROUPS_TLS_PROVIDER_PROPERTY = JGRP_TLS + "-provider";
    public static final String JGROUPS_TLS_KEYSTORE_FILE_PROPERTY = JGRP_TLS + "-keystore-file";
    public static final String JGROUPS_TLS_KEYSTORE_PASSWORD_PROPERTY = JGRP_TLS + "-keystore-password";
    public static final String JGROUPS_TLS_KEYSTORE_TYPE_PROPERTY = JGRP_TLS + "-keystore-type";
    public static final String JGROUPS_TLS_KEYSTORE_ALIAS_PROPERTY = JGRP_TLS + "-keystore-alias";
    public static final String JGROUPS_TLS_TRUSTSTORE_FILE_PROPERTY = JGRP_TLS + "-truststore-file";
    public static final String JGROUPS_TLS_TRUSTSTORE_PASSWORD_PROPERTY = JGRP_TLS + "-truststore-password";
    public static final String JGROUPS_TLS_TRUSTSTORE_TYPE_PROPERTY = JGRP_TLS + "-truststore-type";
    public static final String JGROUPS_TLS_NATIVE_PROPERTY = JGRP_TLS + "-use-native";

    public enum Mechanism {
        ispn,
        local
    }

    public static final Option CACHE = new OptionBuilder<>("cache", Mechanism.class)
            .category(OptionCategory.CACHE)
            .description("Defines the cache mechanism for high-availability. "
                    + "By default in production mode, a 'ispn' cache is used to create a cluster between multiple server nodes. "
                    + "By default in development mode, a 'local' cache disables clustering and is intended for development and testing purposes.")
            .defaultValue(Mechanism.ispn)
            .buildTime(true)
            .build();

    public enum Stack {
        tcp,
        udp,
        kubernetes,
        ec2,
        azure,
        google;
    }

    public static final Option CACHE_STACK = new OptionBuilder<>("cache-stack", Stack.class)
            .category(OptionCategory.CACHE)
            .description("Define the default stack to use for cluster communication and node discovery. This option only takes effect "
                    + "if 'cache' is set to 'ispn'. Default: udp.")
            .buildTime(true)
            .build();

    public static final Option<File> CACHE_CONFIG_FILE = new OptionBuilder<>("cache-config-file", File.class)
            .category(OptionCategory.CACHE)
            .description("Defines the file from which cache configuration should be loaded from. "
                    + "The configuration file is relative to the 'conf/' directory.")
            .buildTime(true)
            .build();

    public static final Option<Boolean> JGROUPS_TLS = new OptionBuilder<>(JGROUPS_TLS_ENABLED_PROPERTY, Boolean.class)
            .category(OptionCategory.CACHE)
            .description("to do")
            .defaultValue(Boolean.FALSE)
            .buildTime(true)
            .build();

    public static final Option<String> JGROUPS_TLS_PROTOCOL = new OptionBuilder<>(JGROUPS_TLS_PROTOCOL_PROPERTY, String.class)
            .category(OptionCategory.CACHE)
            .description("to do")
            .defaultValue("TLSv1.3")
            .expectedValues("TLSv1.2", "TLSv1.3")
            .buildTime(true)
            .build();

    public static final Option<String> JGROUPS_TLS_PROVIDER = new OptionBuilder<>(JGROUPS_TLS_PROVIDER_PROPERTY, String.class)
            .category(OptionCategory.CACHE)
            .description("to do")
            .buildTime(true)
            .build();

    public static final Option<String> JGROUPS_TLS_KEYSTORE = new OptionBuilder<>(JGROUPS_TLS_KEYSTORE_FILE_PROPERTY, String.class)
            .category(OptionCategory.CACHE)
            .description("to do")
            .buildTime(true)
            .build();

    public static final Option<String> JGROUPS_TLS_KEYSTORE_PASSWORD = new OptionBuilder<>(JGROUPS_TLS_KEYSTORE_PASSWORD_PROPERTY, String.class)
            .category(OptionCategory.CACHE)
            .description("to do")
            .buildTime(true)
            .build();

    public static final Option<String> JGROUPS_TLS_KEYSTORE_TYPE = new OptionBuilder<>(JGROUPS_TLS_KEYSTORE_TYPE_PROPERTY, String.class)
            .category(OptionCategory.CACHE)
            .description("to do")
            .defaultValue("pkcs12")
            .buildTime(true)
            .build();

    public static final Option<String> JGROUPS_TLS_KEYSTORE_ALIAS = new OptionBuilder<>(JGROUPS_TLS_KEYSTORE_ALIAS_PROPERTY, String.class)
            .category(OptionCategory.CACHE)
            .description("to do")
            .buildTime(true)
            .build();

    public static final Option<String> JGROUPS_TLS_TRUSTSTORE = new OptionBuilder<>(JGROUPS_TLS_TRUSTSTORE_FILE_PROPERTY, String.class)
            .category(OptionCategory.CACHE)
            .description("to do")
            .buildTime(true)
            .build();

    public static final Option<String> JGROUPS_TLS_TRUSTSTORE_PASSWORD = new OptionBuilder<>(JGROUPS_TLS_TRUSTSTORE_PASSWORD_PROPERTY, String.class)
            .category(OptionCategory.CACHE)
            .description("to do")
            .buildTime(true)
            .build();

    public static final Option<String> JGROUPS_TLS_TRUSTSTORE_TYPE = new OptionBuilder<>(JGROUPS_TLS_TRUSTSTORE_TYPE_PROPERTY, String.class)
            .category(OptionCategory.CACHE)
            .description("to do")
            .defaultValue("pkcs12")
            .buildTime(true)
            .build();

    public static final Option<Boolean> JGROUPS_TLS_NATIVE = new OptionBuilder<>(JGROUPS_TLS_NATIVE_PROPERTY, Boolean.class)
            .category(OptionCategory.CACHE)
            .description("to do")
            .defaultValue(Boolean.FALSE)
            .buildTime(true)
            .build();
}
