package com.byteentropy.iso8583_adapter_core;

import com.byteentropy.iso8583_adapter_core.config.IsoFieldRegistry;
import com.byteentropy.iso8583_adapter_core.config.NettyServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

public class Iso8583AdapterCoreApplication {
    private static final Logger logger = LoggerFactory.getLogger(Iso8583AdapterCoreApplication.class);

    public static void main(String[] args) {
        Properties prop = loadProperties();
        
        // 1. Initialize Registry from JSON before the server starts
        String schemaFile = prop.getProperty("adapter.schema.file", "iso-schema.json");
        try {
            IsoFieldRegistry.loadSchema(schemaFile);
        } catch (Exception e) {
            logger.error("Failed to load ISO schema: {}", e.getMessage());
            System.exit(1);
        }

        // 2. Start TCP Server
        int serverPort = Integer.parseInt(prop.getProperty("adapter.server.port", "8080"));
        try {
            logger.info("Starting ISO 8583 Adapter Engine on port {}...", serverPort);
            new NettyServerConfig(serverPort, prop).start();
        } catch (Exception e) {
            logger.error("Failed to start Netty Server", e);
            System.exit(1);
        }
    }

    private static Properties loadProperties() {
        Properties prop = new Properties();
        // Look for application.properties in the classpath (src/main/resources)
        try (InputStream input = Iso8583AdapterCoreApplication.class
                .getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                prop.load(input);
            } else {
                logger.warn("application.properties not found in classpath, using defaults.");
            }
        } catch (Exception e) {
            logger.error("Error loading properties file", e);
        }
        return prop;
    }
}