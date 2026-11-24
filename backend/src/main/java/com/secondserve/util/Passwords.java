package com.secondserve.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Salted PBKDF2-HMAC-SHA256 password hashing (JDK only) with constant-time comparison. */
public final class Passwords {
    private Passwords() {}
    /** Coursework setting; a production system should use >= 210,000 iterations or Argon2/bcrypt. */
    public static final int ITERATIONS = 30_000;
    private static final SecureRandom RNG = new SecureRandom();

    public static String newSalt() { byte[] b = new byte[16]; RNG.nextBytes(b); return HexFormat.of().formatHex(b); }

    public static String hash(String password, String saltHex) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), HexFormat.of().parseHex(saltHex), ITERATIONS, 256);
            return HexFormat.of().formatHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    public static boolean matches(String password, String saltHex, String expectedHex) {
        return MessageDigest.isEqual(hash(password, saltHex).getBytes(), expectedHex.getBytes());
    }

    public static String newToken() { byte[] b = new byte[32]; RNG.nextBytes(b); return HexFormat.of().formatHex(b); }
}
