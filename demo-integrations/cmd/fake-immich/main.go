// fake-immich serves a read-only subset of the Immich API for the GeoPulse demo.
//
// Each demo user's library is generated from their GeoPulse timeline: photos are taken
// during real stays, so dates always line up with the (shifted) demo data.
package main

import (
	"context"
	"crypto/sha1"
	"encoding/base64"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"slices"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/tess1o/geopulse/demo-integrations/internal/catalog"
	"github.com/tess1o/geopulse/demo-integrations/internal/demodata"
)

const (
	defaultPageSize = 250
	maxPageSize     = 1000
)

type server struct {
	store  *demodata.Store
	photos []catalog.Photo

	mu        sync.Mutex
	libraries map[string]*Library
}

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	photos, err := catalog.Photos()
	if err != nil {
		log.Fatalf("load photo catalog: %v", err)
	}
	store, err := demodata.Open(ctx)
	if err != nil {
		log.Fatalf("open database: %v", err)
	}
	defer store.Close()

	s := &server{store: store, photos: photos, libraries: map[string]*Library{}}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /{$}", s.home)
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, r *http.Request) { w.Write([]byte("ok")) })
	mux.HandleFunc("GET /api/server/ping", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"res": "pong"})
	})
	mux.HandleFunc("GET /api/server/version", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]int{"major": 1, "minor": 140, "patch": 0})
	})
	mux.HandleFunc("POST /api/search/metadata", s.authed(s.searchMetadata))
	mux.HandleFunc("GET /api/albums", s.authed(s.listAlbums))
	mux.HandleFunc("GET /api/albums/{id}", s.authed(s.getAlbum))
	mux.HandleFunc("GET /api/assets/{id}", s.authed(s.getAsset))
	mux.HandleFunc("GET /api/assets/{id}/thumbnail", s.authed(s.thumbnail))
	mux.HandleFunc("GET /api/assets/{id}/original", s.authed(s.original))
	mux.HandleFunc("/api/", func(w http.ResponseWriter, r *http.Request) {
		writeError(w, http.StatusForbidden, "Forbidden", "This demo Immich server is read-only and only supports what GeoPulse uses")
	})

	addr := ":" + envOr("PORT", "2283")
	httpServer := &http.Server{Addr: addr, Handler: logRequests(mux), ReadHeaderTimeout: 10 * time.Second}
	go func() {
		<-ctx.Done()
		shutdown, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		httpServer.Shutdown(shutdown)
	}()
	log.Printf("fake Immich listening on %s with %d photos", addr, len(photos))
	if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatal(err)
	}
}

type handler func(w http.ResponseWriter, r *http.Request, library *Library)

// authed resolves the x-api-key header to a demo user and hands over their library.
func (s *server) authed(next handler) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		user, err := s.store.UserByAPIKey(r.Context(), demodata.Immich, r.Header.Get("x-api-key"))
		if errors.Is(err, demodata.ErrUnknownAPIKey) {
			writeError(w, http.StatusUnauthorized, "Unauthorized", "Invalid API key")
			return
		}
		if err != nil {
			log.Printf("resolve API key: %v", err)
			writeError(w, http.StatusServiceUnavailable, "Service Unavailable", "Demo database is not reachable")
			return
		}
		library, err := s.library(r.Context(), user)
		if err != nil {
			log.Printf("build library for %s: %v", user.Email, err)
			writeError(w, http.StatusServiceUnavailable, "Service Unavailable", "Demo database is not reachable")
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
	library := buildLibrary(snapshot, s.photos)
	s.libraries[user.ID] = library
	log.Printf("built library for %s: %d photos, %d albums (timeline %s)", user.Email, len(library.Assets), len(library.Albums), snapshot.Version)
	return library, nil
}

type searchRequest struct {
	TakenAfter  *time.Time `json:"takenAfter"`
	TakenBefore *time.Time `json:"takenBefore"`
	Type        string     `json:"type"`
	City        string     `json:"city"`
	Country     string     `json:"country"`
	IsFavorite  *bool      `json:"isFavorite"`
	WithExif    bool       `json:"withExif"`
	Order       string     `json:"order"`
	Page        int        `json:"page"`
	Size        int        `json:"size"`
}

func (s *server) searchMetadata(w http.ResponseWriter, r *http.Request, library *Library) {
	var request searchRequest
	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		writeError(w, http.StatusBadRequest, "Bad Request", "Invalid search body: "+err.Error())
		return
	}
	page := max(request.Page, 1)
	size := request.Size
	if size <= 0 {
		size = defaultPageSize
	}
	size = min(size, maxPageSize)

	var matches []*Asset
	if request.Type == "" || strings.EqualFold(request.Type, "IMAGE") {
		for _, asset := range library.Assets {
			if request.TakenAfter != nil && asset.TakenAt.Before(*request.TakenAfter) {
				continue
			}
			if request.TakenBefore != nil && asset.TakenAt.After(*request.TakenBefore) {
				continue
			}
			if request.IsFavorite != nil && asset.Favorite != *request.IsFavorite {
				continue
			}
			if !placeMatches(request.City, asset.Cities, asset.Region, true) || !placeMatches(request.Country, asset.Countries, asset.Region, false) {
				continue
			}
			matches = append(matches, asset)
		}
	}
	if strings.EqualFold(request.Order, "asc") {
		for i, j := 0, len(matches)-1; i < j; i, j = i+1, j-1 {
			matches[i], matches[j] = matches[j], matches[i]
		}
	}

	start := min((page-1)*size, len(matches))
	end := min(start+size, len(matches))
	items := make([]map[string]any, 0, end-start)
	for _, asset := range matches[start:end] {
		items = append(items, assetJSON(asset, library.Owner, request.WithExif))
	}
	var nextPage any
	if end < len(matches) {
		nextPage = strconv.Itoa(page + 1) // Immich sends the next page number as a string
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"albums": map[string]any{"total": 0, "count": 0, "items": []any{}, "facets": []any{}, "nextPage": nil},
		"assets": map[string]any{"total": len(matches), "count": len(items), "items": items, "facets": []any{}, "nextPage": nextPage},
	})
}

// placeAliases maps spellings GeoPulse may send (from GeoNames or reverse geocoding) to a demo region.
var placeAliases = map[string]string{
	"kyiv": "kyiv", "kiev": "kyiv", "київ": "kyiv", "kyiv city": "kyiv", "ukraine": "kyiv", "україна": "kyiv",
	"london": "london", "city of london": "london", "greater london": "london", "лондон": "london",
	"united kingdom": "london", "uk": "london", "england": "london", "great britain": "london",
	"new york": "new-york", "new york city": "new-york", "nyc": "new-york", "manhattan": "new-york", "brooklyn": "new-york",
	"united states": "new-york", "united states of america": "new-york", "usa": "new-york", "us": "new-york",
}

// placeMatches mirrors Immich's city/country filter, but leniently: a photo matches when the
// filter names any spelling of its place (GeoNames or GeoPulse, aliases resolved), and inside
// a demo city an unfamiliar city name is treated as a district. GeoPulse applies its own
// radius filter afterwards.
func placeMatches(filter string, names []string, region string, isCity bool) bool {
	want := canonicalPlace(filter)
	if want == "" {
		return true
	}
	named := false
	for _, name := range names {
		if got := canonicalPlace(name); got != "" {
			named = true
			if got == want {
				return true
			}
		}
	}
	if !named {
		return true
	}
	if region == "" {
		return false
	}
	if slices.Contains(placeAliasRegions, want) {
		return want == region
	}
	// An unfamiliar city name inside a demo city is most likely a district we cannot tell apart.
	return isCity
}

var placeAliasRegions = []string{"kyiv", "london", "new-york"}

func canonicalPlace(name string) string {
	name = strings.ToLower(strings.TrimSpace(name))
	if region, known := placeAliases[name]; known {
		return region
	}
	return name
}

func (s *server) listAlbums(w http.ResponseWriter, r *http.Request, library *Library) {
	albums := make([]map[string]any, 0, len(library.Albums))
	for _, album := range library.Albums {
		albums = append(albums, albumJSON(album, library.Owner, false))
	}
	writeJSON(w, http.StatusOK, albums)
}

func (s *server) getAlbum(w http.ResponseWriter, r *http.Request, library *Library) {
	for _, album := range library.Albums {
		if album.ID == r.PathValue("id") {
			writeJSON(w, http.StatusOK, albumJSON(album, library.Owner, r.URL.Query().Get("withoutAssets") != "true"))
			return
		}
	}
	writeError(w, http.StatusBadRequest, "Bad Request", "Not found or no album.read access")
}

func (s *server) getAsset(w http.ResponseWriter, r *http.Request, library *Library) {
	if asset := library.ByID[r.PathValue("id")]; asset != nil {
		writeJSON(w, http.StatusOK, assetJSON(asset, library.Owner, true))
		return
	}
	writeError(w, http.StatusBadRequest, "Bad Request", "Not found or no asset.read access")
}

func (s *server) thumbnail(w http.ResponseWriter, r *http.Request, library *Library) {
	asset := library.ByID[r.PathValue("id")]
	if asset == nil {
		writeError(w, http.StatusBadRequest, "Bad Request", "Not found or no asset.read access")
		return
	}
	read := catalog.Preview
	if size := r.URL.Query().Get("size"); size == "" || size == "thumbnail" {
		read = catalog.Thumbnail
	}
	serveImage(w, read, asset, "")
}

func (s *server) original(w http.ResponseWriter, r *http.Request, library *Library) {
	asset := library.ByID[r.PathValue("id")]
	if asset == nil {
		writeError(w, http.StatusBadRequest, "Bad Request", "Not found or no asset.read access")
		return
	}
	serveImage(w, catalog.Preview, asset, asset.FileName)
}

func serveImage(w http.ResponseWriter, read func(string) ([]byte, error), asset *Asset, fileName string) {
	data, err := read(asset.Photo.ID)
	if err != nil {
		writeError(w, http.StatusNotFound, "Not Found", "Image file missing")
		return
	}
	w.Header().Set("Content-Type", "image/jpeg")
	w.Header().Set("Cache-Control", "private, max-age=86400, no-transform")
	if fileName != "" {
		w.Header().Set("Content-Disposition", `inline; filename="`+fileName+`"`)
	}
	w.Write(data)
}

func assetJSON(asset *Asset, owner demodata.User, withExif bool) map[string]any {
	local := asset.TakenAt.In(owner.Location)
	localAsUTC := time.Date(local.Year(), local.Month(), local.Day(), local.Hour(), local.Minute(), local.Second(), 0, time.UTC)
	checksum := sha1.Sum([]byte(asset.ID))
	result := map[string]any{
		"id":               asset.ID,
		"deviceAssetId":    asset.FileName + "-" + asset.ID[:8],
		"ownerId":          owner.ID,
		"deviceId":         "geopulse-demo",
		"libraryId":        nil,
		"type":             "IMAGE",
		"originalPath":     "/data/library/demo/" + asset.FileName,
		"originalFileName": asset.FileName,
		"originalMimeType": "image/jpeg",
		"thumbhash":        nil,
		"fileCreatedAt":    asset.TakenAt,
		"fileModifiedAt":   asset.TakenAt,
		"localDateTime":    localAsUTC,
		"updatedAt":        asset.TakenAt,
		"createdAt":        asset.TakenAt,
		"isFavorite":       asset.Favorite,
		"isArchived":       false,
		"isTrashed":        false,
		"isOffline":        false,
		"visibility":       "timeline",
		"duration":         "0:00:00.00000",
		"livePhotoVideoId": nil,
		"tags":             []any{},
		"people":           []any{},
		"checksum":         base64.StdEncoding.EncodeToString(checksum[:]),
		"stack":            nil,
		"duplicateId":      nil,
		"hasMetadata":      true,
		"resized":          true,
		"width":            asset.Photo.Width,
		"height":           asset.Photo.Height,
	}
	if withExif {
		var rating any
		if asset.Rating != nil {
			rating = *asset.Rating
		}
		result["exifInfo"] = map[string]any{
			"make":             asset.Device.make,
			"model":            asset.Device.model,
			"lensModel":        emptyToNil(asset.Device.lens),
			"exifImageWidth":   asset.Photo.Width,
			"exifImageHeight":  asset.Photo.Height,
			"fileSizeInByte":   asset.Photo.SizeBytes,
			"orientation":      "1",
			"dateTimeOriginal": asset.TakenAt,
			"modifyDate":       asset.TakenAt,
			"timeZone":         owner.Location.String(),
			"fNumber":          asset.FNumber,
			"focalLength":      asset.Focal,
			"iso":              asset.ISO,
			"exposureTime":     asset.Exposure,
			"latitude":         asset.Lat,
			"longitude":        asset.Lon,
			"city":             emptyToNil(asset.City),
			"state":            emptyToNil(asset.State),
			"country":          emptyToNil(asset.Country),
			"description":      asset.Photo.Caption,
			"projectionType":   nil,
			"rating":           rating,
		}
	}
	return result
}

func albumJSON(album *Album, owner demodata.User, withAssets bool) map[string]any {
	// Assets are newest first.
	newest, oldest := album.Assets[0], album.Assets[len(album.Assets)-1]
	assets := []map[string]any{}
	if withAssets {
		for _, asset := range album.Assets {
			assets = append(assets, assetJSON(asset, owner, true))
		}
	}
	return map[string]any{
		"id":                         album.ID,
		"albumName":                  album.Name,
		"description":                "",
		"albumThumbnailAssetId":      newest.ID,
		"createdAt":                  oldest.TakenAt,
		"updatedAt":                  newest.TakenAt,
		"ownerId":                    owner.ID,
		"owner":                      map[string]any{"id": owner.ID, "email": owner.Email, "name": strings.Split(owner.Email, "@")[0]},
		"albumUsers":                 []any{},
		"shared":                     false,
		"hasSharedLink":              false,
		"startDate":                  oldest.TakenAt,
		"endDate":                    newest.TakenAt,
		"assets":                     assets,
		"assetCount":                 len(album.Assets),
		"isActivityEnabled":          false,
		"order":                      "desc",
		"lastModifiedAssetTimestamp": newest.TakenAt,
	}
}

func (s *server) home(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.Write([]byte("GeoPulse demo Immich server.\n\nThis is not a real Immich instance. It serves sample photos to the GeoPulse demo and is read-only.\n"))
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(body)
}

func writeError(w http.ResponseWriter, status int, title, message string) {
	writeJSON(w, status, map[string]any{"message": message, "error": title, "statusCode": status})
}

func emptyToNil(value string) any {
	if value == "" {
		return nil
	}
	return value
}

func envOr(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

func logRequests(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		started := time.Now()
		next.ServeHTTP(w, r)
		if os.Getenv("LOG_REQUESTS") == "true" {
			log.Printf("%s %s (%s)", r.Method, r.URL.Path, time.Since(started).Round(time.Millisecond))
		}
	})
}
