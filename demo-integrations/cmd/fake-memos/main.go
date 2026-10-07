// fake-memos serves a read-only subset of the Memos API for the GeoPulse demo.
//
// Memos are generated from each demo user's GeoPulse timeline: they are written during
// real stays, so dates always line up with the (shifted) demo data.
package main

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"html/template"
	"log"
	"net/http"
	"os"
	"os/signal"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/tess1o/geopulse/demo-integrations/internal/demodata"
)

const (
	defaultPageSize = 10
	maxPageSize     = 1000
)

type server struct {
	store *demodata.Store

	mu        sync.Mutex
	libraries map[string]*Library
	byUID     map[string]*Memo // for the public /memos/{uid} pages GeoPulse links to
}

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	store, err := demodata.Open(ctx)
	if err != nil {
		log.Fatalf("open database: %v", err)
	}
	defer store.Close()

	s := &server{store: store, libraries: map[string]*Library{}, byUID: map[string]*Memo{}}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /{$}", s.home)
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, r *http.Request) { w.Write([]byte("ok")) })
	mux.HandleFunc("GET /api/v1/memos", s.authed(s.listMemos))
	mux.HandleFunc("GET /api/v1/memos/{uid}", s.authed(s.getMemo))
	mux.HandleFunc("GET /memos/{uid}", s.memoPage)
	mux.HandleFunc("GET /m/{uid}", s.memoPage)
	mux.HandleFunc("/api/", func(w http.ResponseWriter, r *http.Request) {
		writeError(w, http.StatusForbidden, 7, "this demo Memos server is read-only")
	})

	addr := ":" + envOr("PORT", "5230")
	httpServer := &http.Server{Addr: addr, Handler: mux, ReadHeaderTimeout: 10 * time.Second}
	go func() {
		<-ctx.Done()
		shutdown, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		httpServer.Shutdown(shutdown)
	}()
	log.Printf("fake Memos listening on %s", addr)
	if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatal(err)
	}
}

type handler func(w http.ResponseWriter, r *http.Request, library *Library)

// authed resolves the "Authorization: Bearer <token>" header to a demo user.
func (s *server) authed(next handler) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		token := strings.TrimSpace(strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer "))
		user, err := s.store.UserByAPIKey(r.Context(), demodata.Memos, token)
		if errors.Is(err, demodata.ErrUnknownAPIKey) {
			writeError(w, http.StatusUnauthorized, 16, "invalid access token")
			return
		}
		if err != nil {
			log.Printf("resolve access token: %v", err)
			writeError(w, http.StatusServiceUnavailable, 14, "demo database is not reachable")
			return
		}
		library, err := s.library(r.Context(), user)
		if err != nil {
			log.Printf("build memos for %s: %v", user.Email, err)
			writeError(w, http.StatusServiceUnavailable, 14, "demo database is not reachable")
			return
		}
		next(w, r, library)
	}
}

func (s *server) library(ctx context.Context, user demodata.User) (*Library, error) {
	snapshot, err := s.store.Snapshot(ctx, user)
	if err != nil {
		return nil, err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if library := s.libraries[user.ID]; library != nil && library.Version == snapshot.Version {
		return library, nil
	}
	if previous := s.libraries[user.ID]; previous != nil {
		for _, memo := range previous.Memos {
			delete(s.byUID, memo.UID)
		}
	}
	library := buildLibrary(snapshot)
	for _, memo := range library.Memos {
		s.byUID[memo.UID] = memo
	}
	s.libraries[user.ID] = library
	log.Printf("built memos for %s: %d memos (timeline %s)", user.Email, len(library.Memos), snapshot.Version)
	return library, nil
}

var (
	createdFilter = regexp.MustCompile(`created_ts\s*(>=|<=|>|<)\s*timestamp\((\d+)\)`)
	tagFilter     = regexp.MustCompile(`("(?:[^"\\]|\\.)*")\s+in\s+tags`)
)

// listMemos understands the filter GeoPulse builds:
// created_ts >= timestamp(a) && created_ts <= timestamp(b) && ("x" in tags || "y" in tags)
func (s *server) listMemos(w http.ResponseWriter, r *http.Request, library *Library) {
	query := r.URL.Query()
	filter := query.Get("filter")

	var after, before *time.Time
	for _, match := range createdFilter.FindAllStringSubmatch(filter, -1) {
		seconds, _ := strconv.ParseInt(match[2], 10, 64)
		bound := time.Unix(seconds, 0)
		switch match[1] {
		case ">":
			bound = bound.Add(time.Second)
			fallthrough
		case ">=":
			after = &bound
		case "<":
			bound = bound.Add(-time.Second)
			fallthrough
		case "<=":
			before = &bound
		}
	}
	wantedTags := map[string]bool{}
	for _, match := range tagFilter.FindAllStringSubmatch(filter, -1) {
		if tag, err := strconv.Unquote(match[1]); err == nil {
			wantedTags[tag] = true
		}
	}

	var matches []*Memo
	for _, memo := range library.Memos {
		created := memo.Created.Truncate(time.Second)
		if after != nil && created.Before(*after) || before != nil && created.After(*before) {
			continue
		}
		if len(wantedTags) > 0 && !hasAnyTag(memo, wantedTags) {
			continue
		}
		matches = append(matches, memo)
	}
	if strings.Contains(strings.ToLower(query.Get("orderBy")), "asc") {
		for i, j := 0, len(matches)-1; i < j; i, j = i+1, j-1 {
			matches[i], matches[j] = matches[j], matches[i]
		}
	}

	pageSize, _ := strconv.Atoi(query.Get("pageSize"))
	if pageSize <= 0 {
		pageSize = defaultPageSize
	}
	pageSize = min(pageSize, maxPageSize)
	offset := decodePageToken(query.Get("pageToken"))
	start := min(offset, len(matches))
	end := min(start+pageSize, len(matches))

	memos := make([]map[string]any, 0, end-start)
	for _, memo := range matches[start:end] {
		memos = append(memos, memoJSON(memo))
	}
	nextPageToken := ""
	if end < len(matches) {
		nextPageToken = base64.RawURLEncoding.EncodeToString([]byte("offset:" + strconv.Itoa(end)))
	}
	writeJSON(w, http.StatusOK, map[string]any{"memos": memos, "nextPageToken": nextPageToken})
}

func (s *server) getMemo(w http.ResponseWriter, r *http.Request, library *Library) {
	for _, memo := range library.Memos {
		if memo.UID == r.PathValue("uid") {
			writeJSON(w, http.StatusOK, memoJSON(memo))
			return
		}
	}
	writeError(w, http.StatusNotFound, 5, "memo not found")
}

var memoPageTemplate = template.Must(template.New("memo").Parse(`<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Memo · GeoPulse demo</title>
<style>
body{margin:0;font:16px/1.5 system-ui,sans-serif;background:#f5f5f4;color:#1c1917}
main{max-width:560px;margin:48px auto;padding:0 16px}
article{background:#fff;border-radius:12px;padding:20px 24px;box-shadow:0 1px 3px rgba(0,0,0,.08)}
time,.place,footer{color:#78716c;font-size:14px}
p{white-space:pre-wrap;margin:12px 0}
footer{margin-top:16px}
@media (prefers-color-scheme:dark){body{background:#1c1917;color:#f5f5f4}article{background:#292524}}
</style></head>
<body><main><article>
<time datetime="{{.Created.Format "2006-01-02T15:04:05Z07:00"}}">{{.LocalTime.Format "2 Jan 2006, 15:04 MST"}}</time>
<p>{{.Content}}</p>
{{if .Place}}<div class="place">📍 {{.Place}}</div>{{end}}
</article>
<footer>A sample memo from the GeoPulse demo. This is not a real Memos server.</footer>
</main></body></html>`))

func (s *server) memoPage(w http.ResponseWriter, r *http.Request) {
	s.mu.Lock()
	memo := s.byUID[r.PathValue("uid")]
	s.mu.Unlock()
	if memo == nil {
		http.Error(w, "Memo not found. Demo memos are regenerated when the demo data resets.", http.StatusNotFound)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	memoPageTemplate.Execute(w, memo)
}

func memoJSON(memo *Memo) map[string]any {
	created := memo.Created.Format(time.RFC3339)
	return map[string]any{
		"name":        "memos/" + memo.UID,
		"state":       "NORMAL",
		"creator":     "users/1",
		"createTime":  created,
		"updateTime":  created,
		"displayTime": created,
		"content":     memo.Content,
		"nodes":       []any{},
		"visibility":  "PRIVATE",
		"tags":        memo.Tags,
		"pinned":      false,
		"attachments": []any{},
		"relations":   []any{},
		"reactions":   []any{},
		"property": map[string]any{
			"hasLink":            false,
			"hasTaskList":        strings.Contains(memo.Content, "- ["),
			"hasCode":            false,
			"hasIncompleteTasks": strings.Contains(memo.Content, "- [ ]"),
		},
		"snippet":  snippet(memo.Content),
		"location": map[string]any{"placeholder": memo.Place, "latitude": memo.Lat, "longitude": memo.Lon},
	}
}

var taskMarker = regexp.MustCompile(`- \[[ x]\] `)

func snippet(content string) string {
	text := strings.Join(strings.Fields(taskMarker.ReplaceAllString(content, "")), " ")
	if runes := []rune(text); len(runes) > 100 {
		return string(runes[:100]) + "..."
	}
	return text
}

func hasAnyTag(memo *Memo, wanted map[string]bool) bool {
	for _, tag := range memo.Tags {
		if wanted[tag] {
			return true
		}
	}
	return false
}

func decodePageToken(token string) int {
	raw, err := base64.RawURLEncoding.DecodeString(token)
	if err != nil {
		return 0
	}
	offset, _ := strconv.Atoi(strings.TrimPrefix(string(raw), "offset:"))
	return max(offset, 0)
}

func (s *server) home(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.Write([]byte("GeoPulse demo Memos server.\n\nThis is not a real Memos instance. It serves sample memos to the GeoPulse demo and is read-only.\n"))
}

// writeError uses the gRPC-gateway error shape that Memos returns.
func writeError(w http.ResponseWriter, status, code int, message string) {
	writeJSON(w, status, map[string]any{"code": code, "message": message, "details": []any{}})
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(body)
}

func envOr(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}
