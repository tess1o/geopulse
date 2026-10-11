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
    void createSessionCookie_omitsDomainWhenCookieDomainIsUnsetOrBlank() {
        assertNull(createSessionCookie(Optional.empty()).getDomain());
        assertNull(createSessionCookie(Optional.of("")).getDomain());
        assertNull(createSessionCookie(Optional.of("   ")).getDomain());
        assertNull(createSessionCookie(Optional.of("\"\"")).getDomain());
    }

    @Test
    void createSessionCookie_usesTrimmedCookieDomainWhenConfigured() {
        NewCookie cookie = createSessionCookie(Optional.of("  .example.com  "));

        assertEquals(".example.com", cookie.getDomain());
    }

    private NewCookie createSessionCookie(Optional<String> cookieDomain) {
        CookieService service = new CookieService();
        service.cookieDomain = cookieDomain;
        service.secureCookies = true;

        return service.createSessionCookie("token", 1800);
    }
}
