package com.expensetracker.service;

import org.bouncycastle.crypto.generators.SCrypt;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Service
public class PasswordService {

    private final BCryptPasswordEncoder bcryptEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    private static final String SALT_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /**
     * Verifies a raw password against a stored password hash (supports Werkzeug scrypt, pbkdf2, or BCrypt).
     */
    public boolean verifyPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }

        try {
            if (storedHash.startsWith("scrypt:")) {
                return verifyScrypt(rawPassword, storedHash);
            } else if (storedHash.startsWith("pbkdf2:")) {
                return verifyPbkdf2(rawPassword, storedHash);
            } else if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
                return bcryptEncoder.matches(rawPassword, storedHash);
            } else {
                // Plaintext fallback if any legacy dev passwords exist
                return storedHash.equals(rawPassword);
            }
        } catch (Exception e) {
            System.err.println("Password verification error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Generates a Werkzeug-compatible scrypt password hash.
     */
    public String hashPassword(String rawPassword) {
        int N = 32768;
        int r = 8;
        int p = 1;
        int dkLen = 64;

        String salt = generateRandomSalt(16);
        byte[] pBytes = rawPassword.getBytes(StandardCharsets.UTF_8);
        byte[] sBytes = salt.getBytes(StandardCharsets.UTF_8);

        byte[] derived = SCrypt.generate(pBytes, sBytes, N, r, p, dkLen);
        String hexHash = Hex.toHexString(derived);

        return String.format("scrypt:%d:%d:%d$%s$%s", N, r, p, salt, hexHash);
    }

    private boolean verifyScrypt(String rawPassword, String storedHash) {
        // Expected format: scrypt:N:r:p$salt$hex_hash
        String[] parts = storedHash.split("\\$");
        if (parts.length != 3) {
            return false;
        }

        String paramsPart = parts[0]; // e.g. scrypt:32768:8:1
        String salt = parts[1];
        String expectedHash = parts[2].toLowerCase();

        String[] params = paramsPart.replace("scrypt:", "").split(":");
        if (params.length != 3) {
            return false;
        }

        int N = Integer.parseInt(params[0]);
        int r = Integer.parseInt(params[1]);
        int p = Integer.parseInt(params[2]);
        int dkLen = expectedHash.length() / 2;

        byte[] pBytes = rawPassword.getBytes(StandardCharsets.UTF_8);
        byte[] sBytes = salt.getBytes(StandardCharsets.UTF_8);

        byte[] derived = SCrypt.generate(pBytes, sBytes, N, r, p, dkLen);
        String actualHash = Hex.toHexString(derived).toLowerCase();

        return slowEquals(actualHash, expectedHash);
    }

    private boolean verifyPbkdf2(String rawPassword, String storedHash) {
        // Expected format: pbkdf2:sha256:iterations$salt$hex_hash
        try {
            String[] parts = storedHash.split("\\$");
            if (parts.length != 3) return false;

            String[] algoParts = parts[0].split(":");
            int iterations = Integer.parseInt(algoParts[2]);
            String salt = parts[1];
            String expectedHash = parts[2].toLowerCase();

            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), iterations, expectedHash.length() * 4);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            String actualHash = Hex.toHexString(hash).toLowerCase();

            return slowEquals(actualHash, expectedHash);
        } catch (Exception e) {
            return false;
        }
    }

    private String generateRandomSalt(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SALT_CHARS.charAt(secureRandom.nextInt(SALT_CHARS.length())));
        }
        return sb.toString();
    }

    private boolean slowEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
