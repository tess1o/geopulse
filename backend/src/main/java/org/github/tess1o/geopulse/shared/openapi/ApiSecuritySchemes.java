package org.github.tess1o.geopulse.shared.openapi;

/**
 * OpenAPI security scheme names. The schemes themselves are defined by {@link GeoPulseOpenApiFilter}.
 */
public final class ApiSecuritySchemes {

    public static final String BEARER = "bearerAuth";
    public static final String API_KEY = "apiKey";
    public static final String GPS_SOURCE_BASIC = "gpsSourceBasic";
    public static final String GPS_SOURCE_TOKEN = "gpsSourceToken";
    public static final String GPS_SOURCE_QUERY_KEY = "gpsSourceQueryKey";
    public static final String SHARE_LINK_TOKEN = "shareLinkToken";

    private ApiSecuritySchemes() {
    }
}
