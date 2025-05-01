package org.github.tess1o.geopulse.util;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for password hashing and verification.
 */
@ApplicationScoped
public class PasswordUtils {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 16;
    private static final String DELIMITER = ":";

    /**
     * Hashes a password using SHA-256 with a random salt.
     *
     * @param password The password to hash
     * @return A string containing the salt and hashed password
     */
    public String hashPassword(String password) {
        try {
            // Generate a random salt
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);
            
            // Hash the password with the salt
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            md.update(salt);
            byte[] hashedPassword = md.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // Encode salt and hashed password as Base64 strings
            String saltBase64 = Base64.getEncoder().encodeToString(salt);
            String hashedPasswordBase64 = Base64.getEncoder().encodeToString(hashedPassword);
            
            // Return salt and hashed password concatenated with a delimiter
            return saltBase64 + DELIMITER + hashedPasswordBase64;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    /**
     * Verifies a password against a stored hash.
     *
     * @param password The password to verify
     * @param storedHash The stored hash to verify against
     * @return True if the password matches the hash, false otherwise
     */
    public boolean verifyPassword(String password, String storedHash) {
        try {
            // Split the stored hash into salt and hash
            String[] parts = storedHash.split(DELIMITER);
            if (parts.length != 2) {
                return false;
            }
            
            // Decode the salt and hash from Base64
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] hash = Base64.getDecoder().decode(parts[1]);
            
            // Hash the provided password with the same salt
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            md.update(salt);
            byte[] hashedPassword = md.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // Compare the hashes
            if (hash.length != hashedPassword.length) {
                return false;
            }
            
            // Time-constant comparison to prevent timing attacks
            int diff = 0;
            for (int i = 0; i < hash.length; i++) {
                diff |= hash[i] ^ hashedPassword[i];
            }
            
            return diff == 0;
        } catch (NoSuchAlgorithmException | IllegalArgumentException e) {
            return false;
        }
    }
}