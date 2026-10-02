package org.github.tess1o.geopulse.user.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.mindrot.jbcrypt.BCrypt;

import java.util.UUID;

/**
 * Secure password utility with bcrypt hashing and migration support from SHA-256.
 */
@ApplicationScoped
public class SecurePasswordUtils {

    @Inject
    @ConfigProperty(name = "password.bcrypt.rounds", defaultValue = "12")
    @StaticInitSafe
    int bcryptRounds;

    private String dummyPasswordHash;

    @PostConstruct
    void initializeDummyPasswordHash() {
        dummyPasswordHash = BCrypt.hashpw(UUID.randomUUID().toString(), BCrypt.gensalt(bcryptRounds));
    }

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
            throw new InvalidPasswordException(e);
        }
    }

    public String dummyPasswordHash() {
        return dummyPasswordHash;
    }

}
