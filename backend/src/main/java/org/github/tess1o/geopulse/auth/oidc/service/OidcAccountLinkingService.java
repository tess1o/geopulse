package org.github.tess1o.geopulse.auth.oidc.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.oidc.dto.InitiateOidcLinkingRequest;
import org.github.tess1o.geopulse.auth.oidc.dto.LinkAccountWithPasswordRequest;
import org.github.tess1o.geopulse.auth.oidc.dto.OidcLoginInitResponse;
import org.github.tess1o.geopulse.auth.oidc.model.UserOidcConnectionEntity;
import org.github.tess1o.geopulse.auth.oidc.repository.UserOidcConnectionRepository;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.user.exceptions.UserNotFoundException;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;

import java.time.Instant;
import java.util.Optional;

/**
 * Service for handling OIDC account linking verification flows.
 */
@ApplicationScoped
@Slf4j
public class OidcAccountLinkingService {

    @Inject
    UserService userService;
    
    @Inject
    SecurePasswordUtils securePasswordUtils;
    
    @Inject
    AuthenticationService authenticationService;
    
    @Inject
    OidcLinkingTokenService linkingTokenService;
    
    @Inject
    UserOidcConnectionRepository connectionRepository;
    
    @Inject
    OidcAuthenticationService oidcAuthenticationService;

    /**
     * Link OIDC account using password verification.
     */
    @Transactional
    public AuthResponse linkAccountWithPassword(LinkAccountWithPasswordRequest request) {
        // Validate and consume linking token
        OidcLinkingTokenService.LinkingTokenData tokenData = linkingTokenService.validateAndConsumeToken(request.getLinkingToken());
        if (tokenData == null) {
            throw new IllegalArgumentException("Invalid or expired linking token");
        }
        
        // Verify token data matches request
        if (!tokenData.email().equals(request.getEmail()) || 
            !tokenData.newProvider().equals(request.getProvider())) {
            throw new IllegalArgumentException("Token data mismatch");
        }
        
        // Find user by email
        UserEntity user = userService.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        
        // Verify password
        if (user.getPasswordHash() == null || user.getPasswordHash().trim().isEmpty()) {
            throw new InvalidPasswordException("User does not have a password set");
        }
        
        if (!securePasswordUtils.isPasswordValid(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordException("Invalid password");
        }
        
        // Create the OIDC connection using the original user info
        UserOidcConnectionEntity connection = UserOidcConnectionEntity.builder()
                .userId(user.getId())
                .providerName(tokenData.newProvider())
                .externalUserId(tokenData.originalUserInfo().getSubject())
                .displayName(tokenData.originalUserInfo().getName())
                .avatarUrl(tokenData.originalUserInfo().getPicture())
                .lastLoginAt(Instant.now())
                .build();
        
        connectionRepository.persist(connection);
        
        log.info("Successfully linked {} provider to user {} after password verification", 
                tokenData.newProvider(), user.getEmail());
        
        // Generate auth response
        return authenticationService.createAuthResponse(user);
    }

    /**
     * Initiate OIDC-to-OIDC verification for account linking.
     */
    public OidcLoginInitResponse initiateOidcVerificationForLinking(InitiateOidcLinkingRequest request) {
        // Validate linking token
        OidcLinkingTokenService.LinkingTokenData tokenData = linkingTokenService.validateAndConsumeToken(request.getLinkingToken());
        if (tokenData == null) {
            throw new IllegalArgumentException("Invalid or expired linking token");
        }
        
        // Find user by the original email from the token
        UserEntity user = userService.findByEmail(tokenData.email())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        
        // Verify user has the verification provider linked
        Optional<UserOidcConnectionEntity> verificationConnection = connectionRepository
                .findByUserIdAndProviderName(user.getId(), request.getVerificationProvider());
        
        if (verificationConnection.isEmpty()) {
            throw new IllegalArgumentException("User does not have " + request.getVerificationProvider() + " linked");
        }
        
        // Generate new linking token for the OIDC flow
        String newLinkingToken = linkingTokenService.generateLinkingToken(
            tokenData.email(),
            tokenData.newProvider(),
            tokenData.originalState(),
            tokenData.originalUserInfo()
        );
        
        // Initiate OIDC login for verification with linking context
        return oidcAuthenticationService.initiateLogin(
            request.getVerificationProvider(),
            user.getId(),
            "linking-verification", // Special redirect to handle linking completion
            newLinkingToken // Pass the linking token to preserve context
        );
    }
}