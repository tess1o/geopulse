package org.github.tess1o.geopulse.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.model.entity.UserEntity;
import org.github.tess1o.geopulse.repository.UserRepository;
import org.github.tess1o.geopulse.util.PasswordUtils;

import java.util.Optional;

/**
 * Service for user management operations.
 */
@ApplicationScoped
public class UserService {

    private final UserRepository userRepository;
    private final PasswordUtils passwordUtils;

    @Inject
    public UserService(UserRepository userRepository, PasswordUtils passwordUtils) {
        this.userRepository = userRepository;
        this.passwordUtils = passwordUtils;
    }

    /**
     * Register a new user.
     *
     * @param userId The user ID
     * @param password The password (will be hashed)
     * @param deviceId The device ID (optional)
     * @return The created user entity
     * @throws IllegalArgumentException if the user already exists
     */
    @Transactional
    public UserEntity registerUser(String userId, String password, String deviceId) {
        // Check if user already exists
        if (userRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("User with ID " + userId + " already exists");
        }

        // Create new user entity
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setPasswordHash(passwordUtils.hashPassword(password));
        user.setDeviceId(deviceId);

        // Save user to database
        userRepository.persist(user);
        return user;
    }

    /**
     * Authenticate a user.
     *
     * @param userId The user ID
     * @param password The password to verify
     * @return true if authentication is successful, false otherwise
     */
    public boolean authenticate(String userId, String password) {
        Optional<UserEntity> userOpt = userRepository.findByUserId(userId);
        
        if (userOpt.isEmpty()) {
            return false;
        }
        
        UserEntity user = userOpt.get();
        return passwordUtils.verifyPassword(password, user.getPasswordHash());
    }

    /**
     * Get a user by ID.
     *
     * @param userId The user ID
     * @return Optional containing the user if found
     */
    public Optional<UserEntity> getUserById(String userId) {
        return userRepository.findByUserId(userId);
    }
}