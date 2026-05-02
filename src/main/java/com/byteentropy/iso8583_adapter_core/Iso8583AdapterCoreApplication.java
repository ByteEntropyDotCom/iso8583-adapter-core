package com.byteentropy.iso8583_adapter_core;

import com.byteentropy.iso8583_adapter_core.config.NettyServerConfig;
import java.io.InputStream;
import java.util.Properties;

public class Iso8583AdapterCoreApplication {
    public static void main(String[] args) {
        Properties prop = new Properties();
        int port = 8080; // Default fallback

        try (InputStream input = Iso8583AdapterCoreApplication.class
                .getClassLoader().getResourceAsStream("application.properties")) {
            
            if (input != null) {
                prop.load(input);
                port = Integer.parseInt(prop.getProperty("adapter.server.port", "8080"));
            } else {
                System.err.println("Warning: application.properties not found, using default port 8080");
            }

            new NettyServerConfig(port).start();
            
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }
}