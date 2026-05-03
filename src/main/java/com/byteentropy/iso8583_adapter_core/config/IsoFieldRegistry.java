package com.byteentropy.iso8583_adapter_core.config;

import com.byteentropy.iso8583_adapter_core.model.FieldDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class IsoFieldRegistry {
    private static final Logger logger = LoggerFactory.getLogger(IsoFieldRegistry.class);
    private static final Map<Integer, FieldDefinition> definitions = new ConcurrentHashMap<>();

    public static void loadSchema(String filePath) {
        try {
            InputStream is;
            // 1. Try to find it as a physical file path first
            if (Files.exists(Paths.get(filePath))) {
                is = Files.newInputStream(Paths.get(filePath));
                logger.info("Loading schema from physical path: {}", filePath);
            } else {
                // 2. Fallback: Try to find it in the classpath (resources folder)
                is = IsoFieldRegistry.class.getClassLoader().getResourceAsStream(filePath);
                if (is == null) {
                    throw new RuntimeException("Schema file not found at " + filePath + " or in classpath.");
                }
                logger.info("Loading schema from classpath: {}", filePath);
            }

            try (is) {
                ObjectMapper mapper = new ObjectMapper();
                List<FieldDefinition> list = mapper.readValue(is, new TypeReference<List<FieldDefinition>>() {});
                
                Map<Integer, FieldDefinition> map = list.stream()
                        .collect(Collectors.toMap(FieldDefinition::id, Function.identity()));
                
                definitions.clear();
                definitions.putAll(map);
                logger.info("Successfully loaded {} ISO field definitions", definitions.size());
            }
        } catch (Exception e) {
            logger.error("Failed to load ISO schema: {}", e.getMessage());
            throw new RuntimeException("Schema initialization failed", e);
        }
    }

    public static FieldDefinition getDefinition(int id) {
        return definitions.get(id);
    }
}