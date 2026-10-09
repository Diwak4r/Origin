package np.edu.origin.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * PBKDF2 with HMAC-SHA256, built into the JDK. A random 16-byte salt per user means two
 * people with the same password get different hashes; 120,000 rounds make guessing slow.
 */
public final class PasswordHasher {

    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() { }

    public static String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    public static String hash(String password, String saltHex) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), HexFormat.of().parseHex(saltHex), ITERATIONS, KEY_BITS);
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();
            return HexFormat.of().formatHex(key);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available in this JDK", e);
        }
    }

    /** Constant-time comparison, so the time taken does not reveal how many characters matched. */
    public static boolean matches(String password, String saltHex, String expectedHex) {
        byte[] actual = HexFormat.of().parseHex(hash(password, saltHex));
        byte[] expected = HexFormat.of().parseHex(expectedHex);
        return MessageDigest.isEqual(actual, expected);
    }
}
