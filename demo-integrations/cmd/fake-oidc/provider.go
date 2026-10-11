package main

import (
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"html/template"
	"io"
	"log"
	"math/big"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"time"
)

var realmName = regexp.MustCompile(`^[a-z0-9-]{2,64}$`)

type stats struct {
	Discovery         int `json:"discovery"`
	Authorize         int `json:"authorize"`
	SilentLogins      int `json:"silentLogins"`
	InteractiveLogins int `json:"interactiveLogins"`
	Tokens            int `json:"tokens"`
	EndSessions       int `json:"endSessions"`
}

// realmOptions is the control API body; nil fields keep their current (or default) value.
type realmOptions struct {
	ClientID       *string `json:"clientId"`
	ClientSecret   *string `json:"clientSecret"`
	EndSession     *bool   `json:"endSession"`
	AuthorizeError *string `json:"authorizeError"`
	TokenError     *bool   `json:"tokenError"`
}

type session struct {
	id, subject, email, name string
}

type grant struct {
	session                      *session
	clientID, redirectURI, nonce string
}

type realm struct {
	name           string
	clientID       string
	clientSecret   string
	endSession     bool   // advertise end_session_endpoint (RP-initiated logout)
	authorizeError string // OAuth error the authorize endpoint returns instead of signing in
	tokenError     bool   // the token endpoint rejects every code

	codes    map[string]grant
	sessions map[string]*session
	stats    stats
}

type provider struct {
	baseURL string
	key     *rsa.PrivateKey
	keyID   string

	mu     sync.Mutex
	realms map[string]*realm
}

func newProvider(baseURL string) (*provider, error) {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return nil, err
	}
	return &provider{baseURL: baseURL, key: key, keyID: randomHex(8), realms: map[string]*realm{}}, nil
}

func (p *provider) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, r *http.Request) { w.Write([]byte("ok")) })

	mux.HandleFunc("PUT /__control/realms/{realm}", p.putRealm)
	mux.HandleFunc("PATCH /__control/realms/{realm}", p.inRealm(p.patchRealm))
	mux.HandleFunc("GET /__control/realms/{realm}", p.inRealm(func(w http.ResponseWriter, r *http.Request, rl *realm) {
		writeJSON(w, http.StatusOK, p.describe(rl))
	}))
	mux.HandleFunc("DELETE /__control/realms/{realm}", p.inRealm(func(w http.ResponseWriter, r *http.Request, rl *realm) {
		delete(p.realms, rl.name)
		w.WriteHeader(http.StatusNoContent)
	}))

	mux.HandleFunc("GET /realms/{realm}/.well-known/openid-configuration", p.inRealm(p.discovery))
	mux.HandleFunc("GET /realms/{realm}/authorize", p.inRealm(p.authorize))
	mux.HandleFunc("POST /realms/{realm}/login", p.inRealm(p.login))
	mux.HandleFunc("POST /realms/{realm}/token", p.inRealm(p.token))
	mux.HandleFunc("GET /realms/{realm}/jwks", p.inRealm(p.jwks))
	mux.HandleFunc("GET /realms/{realm}/logout", p.inRealm(p.endSession))
	return mux
}

type realmHandler func(w http.ResponseWriter, r *http.Request, rl *realm)

// inRealm serves a provider endpoint of a registered realm, holding the lock for the whole request.
func (p *provider) inRealm(next realmHandler) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if err := r.ParseForm(); err != nil {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid_request"})
			return
		}
		p.mu.Lock()
		defer p.mu.Unlock()
		rl := p.realms[r.PathValue("realm")]
		if rl == nil {
			writeJSON(w, http.StatusNotFound, map[string]string{"error": "unknown realm"})
			return
		}
		next(w, r, rl)
	}
}

func (p *provider) putRealm(w http.ResponseWriter, r *http.Request) {
	name := r.PathValue("realm")
	if !realmName.MatchString(name) {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid realm name"})
		return
	}
	var options realmOptions
	if err := json.NewDecoder(r.Body).Decode(&options); err != nil && !errors.Is(err, io.EOF) {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
		return
	}
	rl := &realm{
		name:         name,
		clientID:     "geopulse-e2e",
		clientSecret: "geopulse-e2e-secret",
		endSession:   true,
		codes:        map[string]grant{},
		sessions:     map[string]*session{},
	}
	options.apply(rl)

	p.mu.Lock()
	defer p.mu.Unlock()
	p.realms[name] = rl
	writeJSON(w, http.StatusOK, p.describe(rl))
}

func (p *provider) patchRealm(w http.ResponseWriter, r *http.Request, rl *realm) {
	var options realmOptions
	if err := json.NewDecoder(r.Body).Decode(&options); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
		return
	}
	options.apply(rl)
	writeJSON(w, http.StatusOK, p.describe(rl))
}

func (o realmOptions) apply(rl *realm) {
	if o.ClientID != nil {
		rl.clientID = *o.ClientID
	}
	if o.ClientSecret != nil {
		rl.clientSecret = *o.ClientSecret
	}
	if o.EndSession != nil {
		rl.endSession = *o.EndSession
	}
	if o.AuthorizeError != nil {
		rl.authorizeError = *o.AuthorizeError
	}
	if o.TokenError != nil {
		rl.tokenError = *o.TokenError
	}
}

func (p *provider) describe(rl *realm) map[string]any {
	return map[string]any{
		"name":           rl.name,
		"clientId":       rl.clientID,
		"endSession":     rl.endSession,
		"authorizeError": rl.authorizeError,
		"tokenError":     rl.tokenError,
		"discoveryUrl":   p.issuer(rl) + "/.well-known/openid-configuration",
		"stats":          rl.stats,
		"activeSessions": len(rl.sessions),
	}
}

func (p *provider) issuer(rl *realm) string {
	return p.baseURL + "/realms/" + rl.name
}

func (p *provider) discovery(w http.ResponseWriter, r *http.Request, rl *realm) {
	rl.stats.Discovery++
	issuer := p.issuer(rl)
	document := map[string]any{
		"issuer":                                issuer,
		"authorization_endpoint":                issuer + "/authorize",
		"token_endpoint":                        issuer + "/token",
		"jwks_uri":                              issuer + "/jwks",
		"response_types_supported":              []string{"code"},
		"subject_types_supported":               []string{"public"},
		"id_token_signing_alg_values_supported": []string{"RS256"},
		"scopes_supported":                      []string{"openid", "profile", "email"},
		"token_endpoint_auth_methods_supported": []string{"client_secret_post", "client_secret_basic"},
	}
	if rl.endSession {
		document["end_session_endpoint"] = issuer + "/logout"
	}
	writeJSON(w, http.StatusOK, document)
}

func (p *provider) authorize(w http.ResponseWriter, r *http.Request, rl *realm) {
	rl.stats.Authorize++
	query := r.Form
	if query.Get("client_id") != rl.clientID || query.Get("redirect_uri") == "" {
		writeHTML(w, http.StatusBadRequest, "<h1>Unknown client or missing redirect_uri</h1>")
		return
	}
	if rl.authorizeError != "" {
		redirectWith(w, r, query.Get("redirect_uri"), url.Values{
			"error":             {rl.authorizeError},
			"error_description": {fmt.Sprintf("Mock IdP refused the sign-in (%s)", rl.authorizeError)},
			"state":             {query.Get("state")},
		})
		return
	}

	if s := p.browserSession(r, rl); s != nil {
		rl.stats.SilentLogins++
		code := issueCode(rl, s, query)
		redirectWith(w, r, query.Get("redirect_uri"), url.Values{"code": {code}, "state": {query.Get("state")}})
		return
	}

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	if err := loginPage.Execute(w, map[string]string{
		"Action":      p.issuer(rl) + "/login",
		"ClientID":    query.Get("client_id"),
		"RedirectURI": query.Get("redirect_uri"),
		"State":       query.Get("state"),
		"Nonce":       query.Get("nonce"),
	}); err != nil {
		log.Printf("render login page: %v", err)
	}
}

func (p *provider) login(w http.ResponseWriter, r *http.Request, rl *realm) {
	form := r.PostForm
	email := strings.ToLower(strings.TrimSpace(form.Get("email")))
	if form.Get("client_id") != rl.clientID || form.Get("redirect_uri") == "" || email == "" {
		writeHTML(w, http.StatusBadRequest, "<h1>Invalid sign-in request</h1>")
		return
	}
	rl.stats.InteractiveLogins++
	digest := sha256.Sum256([]byte(email))
	s := &session{
		id:      randomHex(16),
		subject: "mock-" + hex.EncodeToString(digest[:])[:24],
		email:   email,
		name:    form.Get("name"),
	}
	if s.name == "" {
		s.name = email
	}
	rl.sessions[s.id] = s
	http.SetCookie(w, &http.Cookie{
		Name: cookieName(rl), Value: s.id, Path: "/realms/" + rl.name,
		HttpOnly: true, SameSite: http.SameSiteLaxMode,
	})
	code := issueCode(rl, s, form)
	redirectWith(w, r, form.Get("redirect_uri"), url.Values{"code": {code}, "state": {form.Get("state")}})
}

func (p *provider) token(w http.ResponseWriter, r *http.Request, rl *realm) {
	rl.stats.Tokens++
	clientID, clientSecret, ok := r.BasicAuth()
	if !ok {
		clientID, clientSecret = r.PostForm.Get("client_id"), r.PostForm.Get("client_secret")
	}
	if clientID != rl.clientID || clientSecret != rl.clientSecret {
		writeJSON(w, http.StatusUnauthorized, map[string]string{"error": "invalid_client"})
		return
	}
	if rl.tokenError {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid_grant", "error_description": "Mock IdP token error"})
		return
	}
	code := r.PostForm.Get("code")
	g, found := rl.codes[code]
	delete(rl.codes, code)
	if r.PostForm.Get("grant_type") != "authorization_code" || !found ||
		g.clientID != clientID || g.redirectURI != r.PostForm.Get("redirect_uri") {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid_grant"})
		return
	}

	now := time.Now().Unix()
	idToken, err := p.signJWT(map[string]any{
		"iss":            p.issuer(rl),
		"aud":            clientID,
		"sub":            g.session.subject,
		"email":          g.session.email,
		"email_verified": true,
		"name":           g.session.name,
		"nonce":          g.nonce,
		"sid":            g.session.id,
		"iat":            now,
		"auth_time":      now,
		"exp":            now + 300,
	})
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "server_error"})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"access_token": randomToken(),
		"token_type":   "Bearer",
		"expires_in":   300,
		"id_token":     idToken,
	})
}

func (p *provider) jwks(w http.ResponseWriter, r *http.Request, rl *realm) {
	writeJSON(w, http.StatusOK, map[string]any{"keys": []map[string]string{{
		"kty": "RSA",
		"kid": p.keyID,
		"alg": "RS256",
		"use": "sig",
		"n":   b64(p.key.N.Bytes()),
		"e":   b64(big.NewInt(int64(p.key.E)).Bytes()),
	}}})
}

func (p *provider) endSession(w http.ResponseWriter, r *http.Request, rl *realm) {
	if !rl.endSession {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "not found"})
		return
	}
	rl.stats.EndSessions++
	if s := p.browserSession(r, rl); s != nil {
		delete(rl.sessions, s.id)
	}
	http.SetCookie(w, &http.Cookie{
		Name: cookieName(rl), Value: "", Path: "/realms/" + rl.name, MaxAge: -1,
		HttpOnly: true, SameSite: http.SameSiteLaxMode,
	})
	if target := r.Form.Get("post_logout_redirect_uri"); target != "" {
		redirectWith(w, r, target, nil)
		return
	}
	writeHTML(w, http.StatusOK, "<h1>Signed out of Mock IdP</h1>")
}

func (p *provider) browserSession(r *http.Request, rl *realm) *session {
	cookie, err := r.Cookie(cookieName(rl))
	if err != nil {
		return nil
	}
	return rl.sessions[cookie.Value]
}

func (p *provider) signJWT(claims map[string]any) (string, error) {
	header, err := json.Marshal(map[string]string{"alg": "RS256", "typ": "JWT", "kid": p.keyID})
	if err != nil {
		return "", err
	}
	payload, err := json.Marshal(claims)
	if err != nil {
		return "", err
	}
	signingInput := b64(header) + "." + b64(payload)
	digest := sha256.Sum256([]byte(signingInput))
	signature, err := rsa.SignPKCS1v15(rand.Reader, p.key, crypto.SHA256, digest[:])
	if err != nil {
		return "", err
	}
	return signingInput + "." + b64(signature), nil
}

func issueCode(rl *realm, s *session, request url.Values) string {
	code := randomToken()
	rl.codes[code] = grant{
		session:     s,
		clientID:    request.Get("client_id"),
		redirectURI: request.Get("redirect_uri"),
		nonce:       request.Get("nonce"),
	}
	return code
}

func cookieName(rl *realm) string {
	return "fake_oidc_" + strings.ReplaceAll(rl.name, "-", "_")
}

func redirectWith(w http.ResponseWriter, r *http.Request, target string, params url.Values) {
	location, err := url.Parse(target)
	if err != nil {
		writeHTML(w, http.StatusBadRequest, "<h1>Invalid redirect URI</h1>")
		return
	}
	query := location.Query()
	for key, values := range params {
		for _, value := range values {
			query.Set(key, value)
		}
	}
	location.RawQuery = query.Encode()
	w.Header().Set("Cache-Control", "no-store")
	http.Redirect(w, r, location.String(), http.StatusFound)
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(body)
}

func writeHTML(w http.ResponseWriter, status int, body string) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	w.Write([]byte(body))
}

func b64(data []byte) string {
	return base64.RawURLEncoding.EncodeToString(data)
}

func randomHex(size int) string {
	buf := make([]byte, size)
	rand.Read(buf)
	return hex.EncodeToString(buf)
}

func randomToken() string {
	buf := make([]byte, 24)
	rand.Read(buf)
	return b64(buf)
}

// The element ids are what the e2e helpers (tests/utils/mock-oidc.js) fill in and click.
var loginPage = template.Must(template.New("login").Parse(`<!doctype html>
<html>
  <head><meta charset="utf-8"><title>Mock IdP sign-in</title></head>
  <body>
    <h1>Mock IdP</h1>
    <form id="mock-oidc-login" method="post" action="{{.Action}}">
      <input type="hidden" name="client_id" value="{{.ClientID}}">
      <input type="hidden" name="redirect_uri" value="{{.RedirectURI}}">
      <input type="hidden" name="state" value="{{.State}}">
      <input type="hidden" name="nonce" value="{{.Nonce}}">
      <label>Email <input id="mock-oidc-email" name="email" type="email" required></label>
      <label>Name <input id="mock-oidc-name" name="name" type="text"></label>
      <button id="mock-oidc-submit" type="submit">Sign in to Mock IdP</button>
    </form>
  </body>
</html>`))
