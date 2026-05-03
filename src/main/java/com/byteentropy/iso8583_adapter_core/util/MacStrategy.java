package com.byteentropy.iso8583_adapter_core.util;

public interface MacStrategy {
    byte[] calculate(byte[] data, String keyAlias, String algorithm) throws Exception;
}