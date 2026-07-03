package org.github.tess1o.geopulse.auth.service;

import jakarta.ws.rs.core.NewCookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit")
class CookieServiceTest {

    @Test
    void createAccessTokenCookie_omitsDomainWhenCookieDomainIsUnsetOrBlank() {
        assertNull(createAccessTokenCookie(Optional.empty()).getDomain());
        assertNull(createAccessTokenCookie(Optional.of("")).getDomain());
        assertNull(createAccessTokenCookie(Optional.of("   ")).getDomain());
        assertNull(createAccessTokenCookie(Optional.of("\"\"")).getDomain());
    }

    @Test
    void createAccessTokenCookie_usesTrimmedCookieDomainWhenConfigured() {
        NewCookie cookie = createAccessTokenCookie(Optional.of("  .example.com  "));

        assertEquals(".example.com", cookie.getDomain());
    }

    private NewCookie createAccessTokenCookie(Optional<String> cookieDomain) {
        CookieService service = new CookieService();
        service.cookieDomain = cookieDomain;
        service.secureCookies = true;
        service.csrfCookieName = "csrf-token";

        return service.createAccessTokenCookie("token", 1800);
    }
}
