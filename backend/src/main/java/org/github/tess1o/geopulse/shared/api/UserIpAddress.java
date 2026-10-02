package org.github.tess1o.geopulse.shared.api;

import io.vertx.core.http.HttpServerRequest;

public class UserIpAddress {

    public static String resolve(HttpServerRequest request) {
        if (request == null) {
            return null;
        }
        return resolve(request, request.getHeader("X-Forwarded-For"), request.getHeader("X-Real-IP"));
    }

    private static String resolve(HttpServerRequest request, String forwardedFor, String realIp) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // X-Forwarded-For can be a comma-separated list; the first is the original client
            return forwardedFor.split(",")[0].trim();
        }
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        if (request != null && request.remoteAddress() != null) {
            return request.remoteAddress().host(); // fallback for local/dev
        }
        return null;
    }
}
