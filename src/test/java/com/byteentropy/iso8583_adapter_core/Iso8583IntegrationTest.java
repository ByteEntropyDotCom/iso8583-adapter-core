package com.byteentropy.iso8583_adapter_core;

import com.byteentropy.iso8583_adapter_core.config.IsoFieldRegistry;
import com.byteentropy.iso8583_adapter_core.decoder.Iso8583Decoder;
import com.byteentropy.iso8583_adapter_core.decoder.MessageFrameDecoder;
import com.byteentropy.iso8583_adapter_core.handler.Iso8583ServerHandler;
import com.byteentropy.iso8583_adapter_core.util.IsoUtil;
import com.byteentropy.iso8583_adapter_core.util.MacStrategy;
import com.byteentropy.iso8583_adapter_core.util.SoftwareMacStrategy;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class Iso8583IntegrationTest {

    private static Properties props;
    private static Iso8583ServerHandler sharedHandler;
    private static MacStrategy testMacStrategy; // Updated to use Strategy interface

    @BeforeAll
    static void setup() throws Exception {
        props = new Properties();
        props.setProperty("adapter.protocol.mti-binary", "false");
        props.setProperty("adapter.protocol.bitmap-binary", "true");
        props.setProperty("adapter.security.mac-enabled", "true");
        props.setProperty("adapter.security.use-hsm", "false"); // Use software for testing
        props.setProperty("adapter.security.mac-key", "super-secret-key-12345");
        props.setProperty("adapter.security.mac-algorithm", "HmacSHA256");
        props.setProperty("adapter.security.mac-field", "128");
        props.setProperty("adapter.security.mac-length", "64");
        props.setProperty("adapter.business.allowed-mtis", "0200,0800");
        props.setProperty("adapter.business.mandatory-fields", "3,11,41");
        props.setProperty("adapter.map.field.response-code", "39");
        props.setProperty("adapter.mti.map.0200", "0210");
        props.setProperty("adapter.mti.map.0800", "0810");
        props.setProperty("adapter.response.mac-error-code", "A0");
        props.setProperty("adapter.response.system-error-code", "96");
        props.setProperty("adapter.protocol.bcd-padding", "RIGHT_ZERO");

        URL resource = Iso8583IntegrationTest.class.getClassLoader().getResource("iso-schema.json");
        if (resource == null) throw new RuntimeException("Schema not found");
        IsoFieldRegistry.loadSchema(Paths.get(resource.toURI()).toString());
        
        sharedHandler = new Iso8583ServerHandler(props);
        testMacStrategy = new SoftwareMacStrategy(); // Initialize the pluggable strategy
    }

    private EmbeddedChannel createPipeline() {
        return new EmbeddedChannel(
            new MessageFrameDecoder(),
            new Iso8583Decoder(props),
            sharedHandler
        );
    }

    private ByteBuf pollForResponse(EmbeddedChannel channel) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            channel.runPendingTasks();
            ByteBuf out = channel.readOutbound();
            if (out != null) return out;
            TimeUnit.MILLISECONDS.sleep(20);
        }
        return null;
    }

    private ByteBuf createFrame(String mti, boolean includeF41, String stan) throws Exception {
        String bitmapHex = includeF41 ? "2020000000800000" : "2020000000000000";
        String fields = "000000" + stan + (includeF41 ? "TERM0001" : "");
        
        byte[] bitmap = IsoUtil.hexToBytes(bitmapHex);
        ByteBuf body = Unpooled.buffer();
        body.writeCharSequence(mti, StandardCharsets.US_ASCII);
        body.writeBytes(bitmap);
        body.writeCharSequence(fields, StandardCharsets.US_ASCII);
        
        byte[] rawBody = new byte[body.readableBytes()];
        body.getBytes(0, rawBody);
        
        // UPDATED: Using testMacStrategy instead of deleted SecurityUtil
        byte[] macRaw = testMacStrategy.calculate(
            rawBody, 
            props.getProperty("adapter.security.mac-key"), 
            props.getProperty("adapter.security.mac-algorithm")
        );
        String mac = IsoUtil.bytesToHex(macRaw);

        ByteBuf frame = Unpooled.buffer();
        frame.writeShort(rawBody.length + 64); 
        frame.writeBytes(rawBody);
        frame.writeCharSequence(mac, StandardCharsets.US_ASCII);
        
        body.release();
        return frame;
    }

    @Test
    @DisplayName("1. Happy Path 0200")
    void testHappyPath() throws Exception {
        EmbeddedChannel channel = createPipeline();
        ByteBuf frame = createFrame("0200", true, "123456");
        channel.writeInbound(frame);
        
        ByteBuf resp = pollForResponse(channel);
        assertNotNull(resp, "Response timed out");
        String resStr = resp.toString(StandardCharsets.US_ASCII);
        assertTrue(resStr.contains("0210"), "Should return 0210. Found: " + resStr);
        resp.release();
    }

    @Test
    @DisplayName("2. Missing Mandatory Field")
    void testMissingMandatoryField() throws Exception {
        EmbeddedChannel channel = createPipeline();
        ByteBuf frame = createFrame("0200", false, "999999");
        channel.writeInbound(frame);
        
        ByteBuf resp = pollForResponse(channel);
        assertNotNull(resp);
        String resStr = resp.toString(StandardCharsets.US_ASCII);
        assertTrue(resStr.contains("96") || resStr.contains("A0"), "Expected rejection code 96 or A0. Found: " + resStr);
        resp.release();
    }

    @Test
    @DisplayName("3. Blocked MTI")
    void testBlockedMti() throws Exception {
        EmbeddedChannel channel = createPipeline();
        ByteBuf frame = createFrame("0300", true, "888888");
        channel.writeInbound(frame);
        
        ByteBuf resp = pollForResponse(channel);
        assertNotNull(resp);
        String resStr = resp.toString(StandardCharsets.US_ASCII);
        assertTrue(resStr.contains("96") || resStr.contains("A0"), "Expected rejection code 96 or A0. Found: " + resStr);
        resp.release();
    }

    @Test
    @DisplayName("4. Invalid MAC")
    void testInvalidMac() throws Exception {
        EmbeddedChannel channel = createPipeline();
        ByteBuf frame = createFrame("0200", true, "111111");
        int lastIdx = frame.writerIndex() - 1;
        frame.setByte(lastIdx, frame.getByte(lastIdx) == '0' ? '1' : '0');

        channel.writeInbound(frame);
        ByteBuf resp = pollForResponse(channel);
        assertNotNull(resp);
        assertTrue(resp.toString(StandardCharsets.US_ASCII).contains("A0"), "Expected MAC error A0");
        resp.release();
    }

    @Test
    @DisplayName("5. Sticky Packets")
    void testStickyPackets() throws Exception {
        EmbeddedChannel channel = createPipeline();
        ByteBuf f1 = createFrame("0200", true, "101010");
        ByteBuf f2 = createFrame("0800", true, "202020");

        channel.writeInbound(Unpooled.buffer().writeBytes(f1).writeBytes(f2));

        ByteBuf r1 = pollForResponse(channel);
        assertNotNull(r1, "First packet failed");
        r1.release();

        ByteBuf r2 = pollForResponse(channel);
        assertNotNull(r2, "Second packet failed");
        r2.release();
    }

    @Test
    @DisplayName("6. Fragmentation")
    void testFragmentation() throws Exception {
        EmbeddedChannel channel = createPipeline();
        ByteBuf full = createFrame("0200", true, "777777");
        
        while (full.isReadable()) {
            channel.writeInbound(full.readRetainedSlice(1));
        }

        ByteBuf resp = pollForResponse(channel);
        assertNotNull(resp);
        resp.release();
        full.release();
    }
}