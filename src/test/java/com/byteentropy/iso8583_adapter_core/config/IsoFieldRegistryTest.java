package com.byteentropy.iso8583_adapter_core.config;

import com.byteentropy.iso8583_adapter_core.model.FieldDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class IsoFieldRegistryTest {

    @BeforeAll
    static void init() throws Exception {
        // Load the production schema from the test resources
        URL resource = IsoFieldRegistryTest.class.getClassLoader().getResource("iso-schema.json");
        if (resource == null) {
            throw new RuntimeException("iso-schema.json not found in src/test/resources");
        }
        IsoFieldRegistry.loadSchema(Paths.get(resource.toURI()).toString());
    }

    @Test
    @DisplayName("Registry: Basic Field Lookup")
    void testRegistryLookup() {
        // Field 3 is a standard ISO field (Processing Code)
        FieldDefinition def = IsoFieldRegistry.getDefinition(3);
        assertNotNull(def, "Field 3 should be present in schema");
        assertEquals(FieldDefinition.FieldType.FIXED, def.type());
        assertEquals(6, def.length(), "Standard ISO Field 3 length is 6");
    }

    @Test
    @DisplayName("Registry: Recursive Sub-field Detection")
    void testSubFieldLogic() {
        // Find a container field (like Field 48 or 127 depending on your JSON)
        FieldDefinition field48 = IsoFieldRegistry.getDefinition(48);
        
        if (field48 != null && field48.subFields() != null) {
            assertTrue(field48.isContainer(), "Field 48 should be identified as a container");
            assertFalse(field48.subFields().isEmpty(), "Field 48 should have sub-definitions");
            
            // Check first sub-field (e.g., 48.1)
            FieldDefinition sub1 = field48.subFields().get(0);
            assertNotNull(sub1, "First sub-field of 48 should not be null");
        }
    }

    @Test
    @DisplayName("Registry: Negative Lookup")
    void testInvalidFieldLookup() {
        // 999 is outside the ISO-8583:1987 range
        FieldDefinition def = IsoFieldRegistry.getDefinition(999);
        assertNull(def, "Undefined field lookup must return null to prevent NullPointerException downstream");
    }

    @Test
    @DisplayName("Model: FieldDefinition Logic")
    void testDefinitionLogic() {
        // Test a manual definition to ensure the 'isContainer' helper works as intended
        FieldDefinition simpleField = new FieldDefinition(
            1, 
            FieldDefinition.FieldType.FIXED, 
            FieldDefinition.Encoding.ASCII, 
            10, 
            "Test", 
            Collections.emptyList()
        );
        
        // A field is only a container if the list is NOT null AND NOT empty
        assertFalse(simpleField.isContainer(), "Empty list should not qualify as a container");
        
        FieldDefinition containerField = new FieldDefinition(
            48, 
            FieldDefinition.FieldType.LLLVAR, 
            FieldDefinition.Encoding.ASCII, 
            999, 
            "Container", 
            Collections.singletonList(simpleField)
        );
        assertTrue(containerField.isContainer(), "Field with sub-fields must be a container");
    }
}