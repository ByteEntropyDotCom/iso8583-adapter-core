package com.byteentropy.iso8583_adapter.integration;

import com.byteentropy.iso8583_adapter_core.decoder.Iso8583Decoder;
import com.byteentropy.iso8583_adapter_core.model.IsoMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class Iso8583IntegrationTest {

    private EmbeddedChannel channel;

    @BeforeEach
    void setup() {
        channel = new EmbeddedChannel(new Iso8583Decoder());
    }

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("End-to-End: Successfully decode a full ISO message")
    void testFullPipelineProcessing() throws Exception {
        String mti = "30323030"; 
        String bitmap = "3020000000010000"; // Fields 3, 4, 11, 48
        String f3 = "313233343536"; 
        String f4 = "303030303030303031303030"; 
        String f11 = "303030303031"; 
        String f48 = "303131" + Hex.encodeHexString("HELLO WORLD".getBytes(StandardCharsets.US_ASCII));

        ByteBuf input = Unpooled.wrappedBuffer(Hex.decodeHex(mti + bitmap + f3 + f4 + f11 + f48));

        channel.writeInbound(input);
        IsoMessage message = channel.readInbound();

        assertNotNull(message);
        assertEquals("0200", message.mti());
        assertEquals("HELLO WORLD", new String(message.fields().get(48), StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("Resilience: Throw exception on malformed length headers")
    void testMalformedLengthHandling() throws Exception {
        // Field 48 is flagged, but length is "ABC"
        String malformedHex = "30323030" + "0000000000010000" + "414243" + "48454c4c4f";
        ByteBuf input = Unpooled.wrappedBuffer(Hex.decodeHex(malformedHex));

        assertThrows(RuntimeException.class, () -> {
            channel.writeInbound(input);
            channel.checkException();
        });
    }

    @Test
    @DisplayName("Resilience: Return null for truncated headers")
    void testTruncatedMessage() {
        // Sending 5 bytes. Decoder requires 12.
        ByteBuf truncated = Unpooled.copiedBuffer("02001", StandardCharsets.US_ASCII);
        
        channel.writeInbound(truncated);
        IsoMessage result = channel.readInbound();
        
        assertNull(result, "Decoder must return null if MTI + Bitmap header is incomplete");
    }
}