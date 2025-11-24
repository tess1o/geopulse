package org.github.tess1o.geopulse.shared.api;

import io.vertx.core.http.HttpServerRequest;

public class UserIpAddress {

    public static String resolve(HttpServerRequest request, String forwardedFor, String realIp) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        if (request.remoteAddress() != null) {
            return request.remoteAddress().host(); // fallback for local/dev
        }
        return null;
    }
}
