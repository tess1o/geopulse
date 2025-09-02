package org.github.tess1o.geopulse.user.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.mindrot.jbcrypt.BCrypt;


/**
 * Secure password utility with bcrypt hashing and migration support from SHA-256.
 */
@ApplicationScoped
@Slf4j
public class SecurePasswordUtils {

    @Inject
    @ConfigProperty(name = "password.bcrypt.rounds", defaultValue = "12")
    int bcryptRounds;

    /**
     * Hash a password using bcrypt (secure method).
     *
     * @param password Plain text password
     * @return bcrypt hash
     */
    public String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(bcryptRounds));
    }


    public boolean isPasswordValid(String password, String storedHash) {
        try {
            return BCrypt.checkpw(password, storedHash);
        } catch (Exception e) {
            log.error("Unable to check if the password is valid", e);
            throw new InvalidPasswordException(e);
        }
    }

}