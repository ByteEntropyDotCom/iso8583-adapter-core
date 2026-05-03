package com.byteentropy.iso8583_adapter_core.util;

import com.byteentropy.iso8583_adapter_core.model.FieldDefinition;

public class IsoUtil {

    public static byte[] hexToBytes(String s) {
        if (s == null || s.length() % 2 != 0) return new byte[0];
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public static String bcdToString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(Integer.toString((b & 0xf0) >> 4, 16));
            sb.append(Integer.toString(b & 0x0f, 16));
        }
        return sb.toString();
    }

        public static byte[] stringToBcd(String s, String strategy) {
            int len = s.length();
            if (len % 2 != 0) {
                if ("LEFT_F".equalsIgnoreCase(strategy)) {
                    s = s + "F"; // Legacy strategy
                } else {
                    s = "0" + s; // Standard strategy (RIGHT_ZERO)
                }
            }
            return hexToBytes(s);
        }

    public static int getPackedLength(int dataLen, FieldDefinition.Encoding encoding) {
        if (encoding == FieldDefinition.Encoding.BCD) {
            return (dataLen + 1) / 2; 
        }
        return dataLen;
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}