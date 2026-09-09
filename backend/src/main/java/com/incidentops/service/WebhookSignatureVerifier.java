package com.incidentops.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class WebhookSignatureVerifier {

    private final String defaultSecret;

    public WebhookSignatureVerifier(@Value("${github.webhook.secret}") String defaultSecret) {
        this.defaultSecret = defaultSecret;
    }

    public boolean verifySignature(String rawBody, String signatureHeader, String secretOverride) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        String expectedHash = signatureHeader.substring(7);
        String secretToUse = (secretOverride != null && !secretOverride.isBlank()) ? secretOverride : defaultSecret;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secretToUse.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String calculatedHash = bytesToHex(rawHash);

            return MessageDigest.isEqual(calculatedHash.getBytes(StandardCharsets.UTF_8), expectedHash.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
