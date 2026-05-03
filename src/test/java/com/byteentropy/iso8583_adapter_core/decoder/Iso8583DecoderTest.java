package com.byteentropy.iso8583_adapter_core.decoder;

import com.byteentropy.iso8583_adapter_core.config.IsoFieldRegistry;
import com.byteentropy.iso8583_adapter_core.model.IsoMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for the Iso8583Decoder.
 * Ensures that the decoder correctly interprets raw bytes into the IsoMessage model
 * based on the provided JSON schema definitions.
 */
class Iso8583DecoderTest {

    @BeforeAll
    static void initRegistry() {
        // Load the schema from the classpath (src/main/resources or src/test/resources)
        IsoFieldRegistry.loadSchema("iso-schema.json");
    }

    @Test
    @DisplayName("Should decode a standard 0800 Network Management message")
    void testStandard0800Decoding() {
        Properties props = new Properties();
        props.setProperty("adapter.protocol.mti-binary", "false");
        props.setProperty("adapter.protocol.bitmap-binary", "true");
        props.setProperty("adapter.security.mac-length", "0");

        Iso8583Decoder decoder = new Iso8583Decoder(props);
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        // 1. MTI: 0800 (4 bytes ASCII)
        byte[] mti = "0800".getBytes(StandardCharsets.US_ASCII);
        
        // 2. Primary Bitmap (8 bytes)
        // Fields: 3 (Processing Code), 11 (STAN), 41 (Terminal ID)
        byte[] bitmap = new byte[8];
        bitmap[0] = 0x20; // Bit 3
        bitmap[1] = 0x20; // Bit 11
        bitmap[5] = (byte) 0x80; // Bit 41

        // 3. Field Data (Aligned with FIXED lengths in schema)
        byte[] f3 = "123456".getBytes(StandardCharsets.US_ASCII); 
        byte[] f11 = "000001".getBytes(StandardCharsets.US_ASCII); 
        byte[] f41 = "TERM0001".getBytes(StandardCharsets.US_ASCII); 

        ByteBuf input = Unpooled.buffer();
        input.writeBytes(mti);
        input.writeBytes(bitmap);
        input.writeBytes(f3);
        input.writeBytes(f11);
        input.writeBytes(f41);

        assertTrue(channel.writeInbound(input));
        IsoMessage msg = channel.readInbound();

        assertNotNull(msg);
        assertEquals("0800", msg.mti());
        assertEquals("123456", new String(msg.getField(3), StandardCharsets.US_ASCII));
        assertEquals("000001", new String(msg.getField(11), StandardCharsets.US_ASCII));
        assertEquals("TERM0001", new String(msg.getField(41), StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("Should decode LLVAR and recursive sub-fields for Field 48")
    void testRecursiveSubFieldDecoding() {
        Properties props = new Properties();
        props.setProperty("adapter.protocol.mti-binary", "false");
        props.setProperty("adapter.protocol.bitmap-binary", "true");
        props.setProperty("adapter.security.mac-length", "0");

        Iso8583Decoder decoder = new Iso8583Decoder(props);
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        ByteBuf input = Unpooled.buffer();
        input.writeBytes("0200".getBytes()); // MTI
        
        byte[] bitmap = new byte[8];
        bitmap[5] = 0x01; // Enable Field 48 (Bit 48)
        input.writeBytes(bitmap);

        // Field 48 (LLLVAR) 
        // 48.1 (FIXED 3: "CVV") + 48.2 (LLVAR: "10" + "MERCHANTID") = 15 total
        input.writeBytes("015".getBytes(StandardCharsets.US_ASCII)); // LLLVAR Header
        input.writeBytes("CVV".getBytes(StandardCharsets.US_ASCII)); // 48.1
        input.writeBytes("10".getBytes(StandardCharsets.US_ASCII));  // 48.2 Len
        input.writeBytes("MERCHANTID".getBytes(StandardCharsets.US_ASCII)); // 48.2 Data

        channel.writeInbound(input);
        IsoMessage msg = channel.readInbound();

        assertNotNull(msg);
        assertArrayEquals("CVV".getBytes(), msg.fields().get("48.1"));
        assertArrayEquals("MERCHANTID".getBytes(), msg.fields().get("48.2"));
    }

    @Test
    @DisplayName("Should handle Variable Length ASCII fields (Field 2 LLVAR)")
    void testVariableLengthDecoding() {
        Properties props = new Properties();
        props.setProperty("adapter.protocol.mti-binary", "false");
        props.setProperty("adapter.protocol.bitmap-binary", "true");
        props.setProperty("adapter.security.mac-length", "0");

        Iso8583Decoder decoder = new Iso8583Decoder(props);
        EmbeddedChannel channel = new EmbeddedChannel(decoder);
        
        ByteBuf input = Unpooled.buffer();
        input.writeBytes("0200".getBytes(StandardCharsets.US_ASCII)); 
        
        byte[] bitmap = new byte[8];
        bitmap[0] = 0x40; // Bit 2 (PAN)
        input.writeBytes(bitmap);
        
        // Field 2 in schema is LLVAR, Encoding: ASCII
        // Length 16 characters -> "16"
        input.writeBytes("16".getBytes(StandardCharsets.US_ASCII)); 
        input.writeBytes("1234567890123456".getBytes(StandardCharsets.US_ASCII)); 

        channel.writeInbound(input);
        IsoMessage msg = channel.readInbound();
        
        assertNotNull(msg, "Decoded message should not be null");
        assertNotNull(msg.getField(2), "Field 2 (PAN) should not be null");
        assertEquals("1234567890123456", new String(msg.getField(2), StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("Should fail gracefully and close channel on corrupted data")
    void testCorruptedData() {
        Properties props = new Properties();
        Iso8583Decoder decoder = new Iso8583Decoder(props);
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        // Send invalid data that does not follow the ISO structure
        ByteBuf junk = Unpooled.copiedBuffer("NOT_VALID_ISO", StandardCharsets.UTF_8);
        
        channel.writeInbound(junk);
        
        // Decoders typically close the context upon fatal parsing errors
        assertFalse(channel.isOpen(), "Channel should close on decoding error");
    }
}