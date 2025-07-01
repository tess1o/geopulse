package org.github.tess1o.geopulse.auth.service;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class SecurityHeadersFilter implements ContainerResponseFilter {
    
    @Inject
    @ConfigProperty(name = "geopulse.auth.secure-cookies", defaultValue = "false")
    boolean secureCookies;
    
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        // Content Security Policy - stricter in production
        if (secureCookies) {
            // Production CSP - stricter
            responseContext.getHeaders().add("Content-Security-Policy", 
                "default-src 'self'; " +
                "script-src 'self'; " +
                "style-src 'self'; " +
                "font-src 'self'; " +
                "img-src 'self' data: https:; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'");
        } else {
            // Development CSP - more permissive
            responseContext.getHeaders().add("Content-Security-Policy", 
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdnjs.cloudflare.com https://unpkg.com; " +
                "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://unpkg.com; " +
                "font-src 'self' https://cdnjs.cloudflare.com; " +
                "img-src 'self' data: https: blob:; " +
                "connect-src 'self' https:; " +
                "frame-ancestors 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'");
        }
        
        // Prevent clickjacking
        responseContext.getHeaders().add("X-Frame-Options", "DENY");
        
        // Prevent MIME type sniffing
        responseContext.getHeaders().add("X-Content-Type-Options", "nosniff");
        
        // XSS Protection (legacy browsers)
        responseContext.getHeaders().add("X-XSS-Protection", "1; mode=block");
        
        // Referrer Policy
        responseContext.getHeaders().add("Referrer-Policy", "strict-origin-when-cross-origin");
        
        // Permissions Policy (formerly Feature Policy)
        responseContext.getHeaders().add("Permissions-Policy", 
            "camera=(), microphone=(), geolocation=(self), payment=()");
        
        // HSTS (only in production with HTTPS)
        if (secureCookies) {
            responseContext.getHeaders().add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        
        // Remove server identification headers
        responseContext.getHeaders().remove("Server");
        responseContext.getHeaders().remove("X-Powered-By");
    }
}