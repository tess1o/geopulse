package main

import (
	"crypto"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"io"
	"math/big"
	"net/http"
	"net/http/cookiejar"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
)

const callbackURL = "http://geopulse.test/oidc/callback"

type harness struct {
	t      *testing.T
	server *httptest.Server
	client *http.Client // browser: keeps cookies, does not follow redirects
}

func newHarness(t *testing.T) *harness {
	t.Helper()
	p, err := newProvider("")
	if err != nil {
		t.Fatal(err)
	}
	server := httptest.NewServer(p.routes())
	t.Cleanup(server.Close)
	p.baseURL = server.URL
	jar, _ := cookiejar.New(nil)
	client := &http.Client{
		Jar:           jar,
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	}
	return &harness{t: t, server: server, client: client}
}

func (h *harness) do(method, path string, body io.Reader, contentType string) *http.Response {
	h.t.Helper()
	request, err := http.NewRequest(method, h.server.URL+path, body)
	if err != nil {
		h.t.Fatal(err)
	}
	if contentType != "" {
		request.Header.Set("Content-Type", contentType)
	}
	response, err := h.client.Do(request)
	if err != nil {
		h.t.Fatal(err)
	}
	h.t.Cleanup(func() { response.Body.Close() })
	return response
}

func (h *harness) control(method, realm, body string) map[string]any {
	h.t.Helper()
	response := h.do(method, "/__control/realms/"+realm, strings.NewReader(body), "application/json")
	if response.StatusCode != http.StatusOK {
		h.t.Fatalf("%s realm %s: status %d", method, realm, response.StatusCode)
	}
	var described map[string]any
	json.NewDecoder(response.Body).Decode(&described)
	return described
}

func (h *harness) discovery(realm string) map[string]any {
	h.t.Helper()
	var document map[string]any
	json.NewDecoder(h.do("GET", "/realms/"+realm+"/.well-known/openid-configuration", nil, "").Body).Decode(&document)
	return document
}

func (h *harness) authorize(realm, state string) *http.Response {
	h.t.Helper()
	query := url.Values{
		"response_type": {"code"}, "client_id": {"geopulse-e2e"}, "redirect_uri": {callbackURL},
		"scope": {"openid profile email"}, "state": {state}, "nonce": {"nonce-" + state},
	}
	return h.do("GET", "/realms/"+realm+"/authorize?"+query.Encode(), nil, "")
}

func (h *harness) signIn(realm, state, email string) url.Values {
	h.t.Helper()
	form := url.Values{
		"client_id": {"geopulse-e2e"}, "redirect_uri": {callbackURL},
		"state": {state}, "nonce": {"nonce-" + state}, "email": {email},
	}
	response := h.do("POST", "/realms/"+realm+"/login", strings.NewReader(form.Encode()), "application/x-www-form-urlencoded")
	return callbackParams(h.t, response)
}

func (h *harness) exchange(realm, code string) *http.Response {
	h.t.Helper()
	form := url.Values{
		"grant_type": {"authorization_code"}, "code": {code}, "redirect_uri": {callbackURL},
		"client_id": {"geopulse-e2e"}, "client_secret": {"geopulse-e2e-secret"},
	}
	return h.do("POST", "/realms/"+realm+"/token", strings.NewReader(form.Encode()), "application/x-www-form-urlencoded")
}

func callbackParams(t *testing.T, response *http.Response) url.Values {
	t.Helper()
	if response.StatusCode != http.StatusFound {
		t.Fatalf("expected a redirect to the callback, got %d", response.StatusCode)
	}
	location, err := url.Parse(response.Header.Get("Location"))
	if err != nil || !strings.HasPrefix(location.String(), callbackURL) {
		t.Fatalf("unexpected redirect %q", response.Header.Get("Location"))
	}
	return location.Query()
}

// verifyIDToken checks the signature against the published JWKS, as GeoPulse does, and returns the claims.
func (h *harness) verifyIDToken(realm, token string) map[string]any {
	h.t.Helper()
	var keys struct {
		Keys []struct{ Kid, N, E string }
	}
	json.NewDecoder(h.do("GET", "/realms/"+realm+"/jwks", nil, "").Body).Decode(&keys)
	if len(keys.Keys) != 1 {
		h.t.Fatalf("expected one signing key, got %d", len(keys.Keys))
	}
	n, _ := base64.RawURLEncoding.DecodeString(keys.Keys[0].N)
	e, _ := base64.RawURLEncoding.DecodeString(keys.Keys[0].E)
	publicKey := &rsa.PublicKey{N: new(big.Int).SetBytes(n), E: int(new(big.Int).SetBytes(e).Int64())}

	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		h.t.Fatalf("malformed token %q", token)
	}
	signature, _ := base64.RawURLEncoding.DecodeString(parts[2])
	digest := sha256.Sum256([]byte(parts[0] + "." + parts[1]))
	if err := rsa.VerifyPKCS1v15(publicKey, crypto.SHA256, digest[:], signature); err != nil {
		h.t.Fatalf("ID token signature does not verify: %v", err)
	}
	var header map[string]string
	headerJSON, _ := base64.RawURLEncoding.DecodeString(parts[0])
	json.Unmarshal(headerJSON, &header)
	if header["kid"] != keys.Keys[0].Kid || header["alg"] != "RS256" {
		h.t.Errorf("unexpected token header %v", header)
	}
	var claims map[string]any
	payload, _ := base64.RawURLEncoding.DecodeString(parts[1])
	json.Unmarshal(payload, &claims)
	return claims
}

func TestCodeFlowIssuesAVerifiableIDToken(t *testing.T) {
	h := newHarness(t)
	h.control("PUT", "e2e-oidc-flow", `{}`)

	document := h.discovery("e2e-oidc-flow")
	issuer := h.server.URL + "/realms/e2e-oidc-flow"
	if document["issuer"] != issuer || document["end_session_endpoint"] != issuer+"/logout" {
		t.Fatalf("unexpected discovery document %v", document)
	}

	form := h.authorize("e2e-oidc-flow", "s1")
	page, _ := io.ReadAll(form.Body)
	if form.StatusCode != http.StatusOK || !strings.Contains(string(page), `id="mock-oidc-login"`) {
		t.Fatalf("expected the login form, got %d", form.StatusCode)
	}

	callback := h.signIn("e2e-oidc-flow", "s1", "Someone@Example.test")
	if callback.Get("state") != "s1" || callback.Get("code") == "" {
		t.Fatalf("unexpected callback parameters %v", callback)
	}

	response := h.exchange("e2e-oidc-flow", callback.Get("code"))
	if response.StatusCode != http.StatusOK {
		t.Fatalf("token exchange failed with %d", response.StatusCode)
	}
	var tokens map[string]any
	json.NewDecoder(response.Body).Decode(&tokens)
	claims := h.verifyIDToken("e2e-oidc-flow", tokens["id_token"].(string))
	for name, want := range map[string]any{
		"iss": issuer, "aud": "geopulse-e2e", "nonce": "nonce-s1", "email": "someone@example.test",
	} {
		if claims[name] != want {
			t.Errorf("claim %s = %v, want %v", name, claims[name], want)
		}
	}
	if claims["sid"] == "" || claims["sub"] == "" || claims["exp"] == nil {
		t.Errorf("missing sid, sub or exp in %v", claims)
	}

	if replay := h.exchange("e2e-oidc-flow", callback.Get("code")); replay.StatusCode != http.StatusBadRequest {
		t.Errorf("a code must work once, replay got %d", replay.StatusCode)
	}
}

func TestProviderSessionMakesSignInSilentUntilEndSession(t *testing.T) {
	h := newHarness(t)
	h.control("PUT", "e2e-oidc-session", `{}`)
	h.signIn("e2e-oidc-session", "s1", "user@example.test")

	if params := callbackParams(t, h.authorize("e2e-oidc-session", "s2")); params.Get("code") == "" {
		t.Fatal("expected a silent sign-in while the provider session lives")
	}

	logout := h.do("GET", "/realms/e2e-oidc-session/logout?post_logout_redirect_uri="+
		url.QueryEscape("http://geopulse.test/login"), nil, "")
	if logout.StatusCode != http.StatusFound || logout.Header.Get("Location") != "http://geopulse.test/login" {
		t.Fatalf("end session: %d to %q", logout.StatusCode, logout.Header.Get("Location"))
	}

	if form := h.authorize("e2e-oidc-session", "s3"); form.StatusCode != http.StatusOK {
		t.Fatalf("expected the login form after end session, got %d", form.StatusCode)
	}
	described := h.control("GET", "e2e-oidc-session", "")
	stats := described["stats"].(map[string]any)
	if stats["authorize"] != 2.0 || stats["silentLogins"] != 1.0 || stats["interactiveLogins"] != 1.0 ||
		stats["endSessions"] != 1.0 || described["activeSessions"] != 0.0 {
		t.Errorf("unexpected realm state %v", described)
	}
}

func TestRealmWithoutEndSessionDoesNotAdvertiseIt(t *testing.T) {
	h := newHarness(t)
	h.control("PUT", "e2e-oidc-no-slo", `{"endSession": false}`)

	if _, found := h.discovery("e2e-oidc-no-slo")["end_session_endpoint"]; found {
		t.Error("end_session_endpoint must not be advertised")
	}
	if logout := h.do("GET", "/realms/e2e-oidc-no-slo/logout", nil, ""); logout.StatusCode != http.StatusNotFound {
		t.Errorf("logout endpoint should not exist, got %d", logout.StatusCode)
	}
}

func TestInjectedFailures(t *testing.T) {
	h := newHarness(t)
	h.control("PUT", "e2e-oidc-errors", `{"authorizeError": "access_denied"}`)

	params := callbackParams(t, h.authorize("e2e-oidc-errors", "s1"))
	if params.Get("error") != "access_denied" || params.Get("state") != "s1" ||
		params.Get("error_description") != "Mock IdP refused the sign-in (access_denied)" {
		t.Errorf("unexpected error redirect %v", params)
	}

	h.control("PATCH", "e2e-oidc-errors", `{"authorizeError": "", "tokenError": true}`)
	callback := h.signIn("e2e-oidc-errors", "s2", "user@example.test")
	if response := h.exchange("e2e-oidc-errors", callback.Get("code")); response.StatusCode != http.StatusBadRequest {
		t.Errorf("token endpoint should fail, got %d", response.StatusCode)
	}
}

func TestTokenEndpointRejectsAWrongClientSecret(t *testing.T) {
	h := newHarness(t)
	h.control("PUT", "e2e-oidc-client", `{"clientSecret": "right"}`)
	callback := h.signIn("e2e-oidc-client", "s1", "user@example.test")

	if response := h.exchange("e2e-oidc-client", callback.Get("code")); response.StatusCode != http.StatusUnauthorized {
		t.Errorf("expected invalid_client, got %d", response.StatusCode)
	}
}

func TestUnknownRealmsAndNamesAreRejected(t *testing.T) {
	h := newHarness(t)
	if response := h.do("GET", "/realms/missing/.well-known/openid-configuration", nil, ""); response.StatusCode != http.StatusNotFound {
		t.Errorf("unknown realm: got %d", response.StatusCode)
	}
	if response := h.do("PUT", "/__control/realms/Bad_Name", strings.NewReader(`{}`), "application/json"); response.StatusCode != http.StatusBadRequest {
		t.Errorf("invalid realm name: got %d", response.StatusCode)
	}
}
