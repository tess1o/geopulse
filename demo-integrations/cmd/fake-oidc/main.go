// fake-oidc is a mock OpenID Connect provider for GeoPulse's end-to-end tests (OIDC sign-in,
// auto-launch, single logout).
//
// Each test registers its own realm through the control API, so tests never share provider state:
//
//	PUT    /__control/realms/{realm}   {"clientId","clientSecret","endSession","authorizeError","tokenError"}
//	PATCH  /__control/realms/{realm}   any of those fields
//	GET    /__control/realms/{realm}   configuration, request counters, active provider sessions
//	DELETE /__control/realms/{realm}
//
// A realm behaves like a real provider: it keeps a browser session (cookie), so signing in again
// while that session lives is silent, and its end_session endpoint (when enabled) ends it.
//
// It signs an ID token for any email typed into its form. The container only starts it when
// FAKE_OIDC_ENABLED=true, and it must never be configured as a provider outside tests.
package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	port := envOr("PORT", "9400")
	// The issuer and the endpoints the provider advertises: the GeoPulse backend and the browser must
	// both reach it under this URL.
	baseURL := strings.TrimRight(envOr("FAKE_OIDC_BASE_URL", "http://localhost:"+port), "/")
	p, err := newProvider(baseURL)
	if err != nil {
		log.Fatalf("create signing key: %v", err)
	}

	addr := ":" + port
	httpServer := &http.Server{Addr: addr, Handler: p.routes(), ReadHeaderTimeout: 10 * time.Second}
	go func() {
		<-ctx.Done()
		shutdown, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		httpServer.Shutdown(shutdown)
	}()
	log.Printf("fake OIDC provider listening on %s, issuer base %s", addr, baseURL)
	if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatal(err)
	}
}

func envOr(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}
