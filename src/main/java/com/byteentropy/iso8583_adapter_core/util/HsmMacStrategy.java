package com.byteentropy.iso8583_adapter_core.util;

public class HsmMacStrategy implements MacStrategy {
    @Override
    public byte[] calculate(byte[] data, String keyAlias, String algorithm) throws Exception {
        // In the future, this is where PKCS#11 / HSM logic goes:
        // 1. Get Key from HSM KeyStore using the alias
        // 2. Initialize Mac using the HSM Provider
        throw new UnsupportedOperationException("HSM Hardware not detected. Check PKCS11 configuration.");
    }
}