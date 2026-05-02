package com.byteentropy.iso8583_adapter.decoder;

import com.byteentropy.iso8583_adapter_core.decoder.Iso8583Decoder;
import com.byteentropy.iso8583_adapter_core.model.IsoMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class Iso8583DecoderTest {

    @Test
    void testSuccessfulDecode() throws Exception {
        // --- 1. MTI ---
        String mti = "30323030"; // "0200"
        
        // --- 2. BITMAP: 3020000000010000 ---
        // Byte 1: 0x30 (0011 0000) -> Fields 3, 4
        // Byte 2: 0x20 (0010 0000) -> Field 11
        // Byte 6: 0x01 (0000 0001) -> Field 48
        String bitmap = "3020000000010000"; 
        
        // --- 3. FIELD DATA ---
        String f3  = "313233343536";             // "123456"
        String f4  = "303030303030303031303030"; // "000000001000"
        String f11 = "303030303031";             // "000001"
        
        // Field 48 (LLLVAR): "011" + "HELLO WORLD"
        String f48_len = "303131"; 
        String f48_val = Hex.encodeHexString("HELLO WORLD".getBytes(StandardCharsets.US_ASCII));
        
        String fullHex = mti + bitmap + f3 + f4 + f11 + f48_len + f48_val;
        
        ByteBuf input = Unpooled.wrappedBuffer(Hex.decodeHex(fullHex));
        EmbeddedChannel channel = new EmbeddedChannel(new Iso8583Decoder());

        // --- EXECUTE ---
        channel.writeInbound(input);
        IsoMessage result = channel.readInbound();

        // --- ASSERTIONS ---
        assertNotNull(result, "Decoder produced null IsoMessage");
        assertEquals("0200", result.mti());
        
        assertNotNull(result.fields().get(3), "Field 3 is missing");
        assertEquals("123456", new String(result.fields().get(3), StandardCharsets.US_ASCII));
        
        assertNotNull(result.fields().get(4), "Field 4 is missing");
        assertEquals("000000001000", new String(result.fields().get(4), StandardCharsets.US_ASCII));
        
        assertNotNull(result.fields().get(11), "Field 11 is missing");
        
        assertNotNull(result.fields().get(48), "Field 48 is missing");
        assertEquals("HELLO WORLD", new String(result.fields().get(48), StandardCharsets.US_ASCII));
        
        channel.finish();
    }

    @Test
    void testIncompleteMessage() {
        ByteBuf input = Unpooled.copiedBuffer("020", StandardCharsets.US_ASCII);
        EmbeddedChannel channel = new EmbeddedChannel(new Iso8583Decoder());
        channel.writeInbound(input);
        IsoMessage result = channel.readInbound();
        assertNull(result, "Should return null for truncated MTI");
        channel.finish();
    }
}