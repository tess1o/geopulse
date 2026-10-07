// Package demodata reads the demo users' timelines from the GeoPulse database.
//
// The fake servers never store dates of their own. Every photo and memo is anchored to a
// timeline stay, so when the demo reset script shifts the stays forward, the photos and
// memos move with them. The store notices a reset or a timeline rebuild by watching each
// user's stay "version".
package demodata

import (
	"context"
	"errors"
	"fmt"
	"net/url"
	"os"
	"sync"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Integration names the users column that holds the integration's preferences.
type Integration string

const (
	Immich Integration = "immich_preferences"
	Memos  Integration = "memos_preferences"
)

const (
	versionCheckInterval = 5 * time.Second
	userCacheTTL         = time.Minute
)

var ErrUnknownAPIKey = errors.New("unknown API key")

type User struct {
	ID       string
	Email    string
	Location *time.Location
}

type Stay struct {
	// Key identifies the stay across resets and timeline rebuilds: seconds from the user's
	// first GPS point to the stay's start. Stay ids are not stable, because the timeline job
	// replaces stays when it rebuilds them. A reset shifts the GPS points and the stays by the
	// same number of days, so the key stays the same.
	Key      int64
	Start    time.Time
	Duration time.Duration
	Name     string
	Lat      float64
	Lon      float64
	City     string
	Country  string
	// GeoNamesCity and GeoNamesCountry are the nearest GeoNames city, which is how real Immich
	// names a photo's place and what GeoPulse sends as the search filter for coordinates.
	// Empty when the GeoNames import has not run.
	GeoNamesCity    string
	GeoNamesCountry string
}

// Snapshot is one user's timeline at a given version. Callers may cache anything derived
// from it under Version and rebuild when the version changes.
type Snapshot struct {
	User    User
	Stays   []Stay
	Anchor  time.Time // the user's first GPS point; stay keys are measured from it
	Version string
}

type Store struct {
	pool *pgxpool.Pool

	mu        sync.Mutex
	keys      map[string]cachedUser
	snapshots map[string]*cachedSnapshot
}

type cachedUser struct {
	user      User
	expiresAt time.Time
}

type cachedSnapshot struct {
	snapshot  *Snapshot
	checkedAt time.Time
}

// Open connects using DEMO_DATABASE_URL, or the GEOPULSE_POSTGRES_* variables that the
// GeoPulse compose files already define.
func Open(ctx context.Context) (*Store, error) {
	dsn := os.Getenv("DEMO_DATABASE_URL")
	if dsn == "" {
		dsn = (&url.URL{
			Scheme:   "postgres",
			User:     url.UserPassword(env("GEOPULSE_POSTGRES_USERNAME", "geopulse-user"), os.Getenv("GEOPULSE_POSTGRES_PASSWORD")),
			Host:     env("GEOPULSE_POSTGRES_HOST", "geopulse-postgres") + ":" + env("GEOPULSE_POSTGRES_PORT", "5432"),
			Path:     "/" + env("GEOPULSE_POSTGRES_DB", "geopulse"),
			RawQuery: "sslmode=" + env("GEOPULSE_POSTGRES_SSLMODE", "disable"),
		}).String()
	}
	config, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		return nil, fmt.Errorf("database config: %w", err)
	}
	config.MaxConns = 4
	config.MaxConnIdleTime = time.Minute
	pool, err := pgxpool.NewWithConfig(ctx, config)
	if err != nil {
		return nil, err
	}
	return &Store{pool: pool, keys: map[string]cachedUser{}, snapshots: map[string]*cachedSnapshot{}}, nil
}

func env(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

func (s *Store) Close() { s.pool.Close() }

// UserByAPIKey finds the GeoPulse user whose saved integration settings use apiKey.
// The demo database is the only place the keys live, so there is nothing to keep in sync.
func (s *Store) UserByAPIKey(ctx context.Context, integration Integration, apiKey string) (User, error) {
	if apiKey == "" {
		return User{}, ErrUnknownAPIKey
	}
	cacheKey := string(integration) + "\x00" + apiKey
	s.mu.Lock()
	cached, ok := s.keys[cacheKey]
	s.mu.Unlock()
	if ok && time.Now().Before(cached.expiresAt) {
		return cached.user, nil
	}

	var user User
	var timezone string
	// integration is one of the two constants above, never user input.
	query := fmt.Sprintf(`SELECT id::text, email, timezone FROM users WHERE %s->>'apiKey' = $1 LIMIT 1`, integration)
	err := s.withRetry(ctx, func() error {
		return s.pool.QueryRow(ctx, query, apiKey).Scan(&user.ID, &user.Email, &timezone)
	})
	if errors.Is(err, pgx.ErrNoRows) {
		return User{}, ErrUnknownAPIKey
	}
	if err != nil {
		return User{}, err
	}
	user.Location, err = time.LoadLocation(timezone)
	if err != nil {
		user.Location = time.UTC
	}

	s.mu.Lock()
	s.keys[cacheKey] = cachedUser{user: user, expiresAt: time.Now().Add(userCacheTTL)}
	s.mu.Unlock()
	return user, nil
}

// Snapshot returns the user's stays, reloading them only when the timeline changed.
func (s *Store) Snapshot(ctx context.Context, user User) (*Snapshot, error) {
	s.mu.Lock()
	cached := s.snapshots[user.ID]
	s.mu.Unlock()
	if cached != nil && time.Since(cached.checkedAt) < versionCheckInterval {
		return cached.snapshot, nil
	}

	version, err := s.version(ctx, user.ID)
	if err != nil {
		if cached != nil {
			// The database is briefly unavailable while the reset script swaps it; keep serving.
			return cached.snapshot, nil
		}
		return nil, err
	}
	if cached != nil && cached.snapshot.Version == version {
		s.mu.Lock()
		cached.checkedAt = time.Now()
		s.mu.Unlock()
		return cached.snapshot, nil
	}

	stays, anchor, err := s.loadStays(ctx, user.ID)
	if err != nil {
		return nil, err
	}
	snapshot := &Snapshot{User: user, Stays: stays, Anchor: anchor, Version: version}
	s.mu.Lock()
	s.snapshots[user.ID] = &cachedSnapshot{snapshot: snapshot, checkedAt: time.Now()}
	s.mu.Unlock()
	return snapshot, nil
}

func (s *Store) version(ctx context.Context, userID string) (string, error) {
	var count, maxID int64
	var latest time.Time
	err := s.withRetry(ctx, func() error {
		return s.pool.QueryRow(ctx, `
			SELECT count(*), coalesce(max(id), 0), coalesce(max(timestamp), 'epoch'::timestamptz)
			  FROM timeline_stays
			 WHERE user_id = $1::uuid`, userID).Scan(&count, &maxID, &latest)
	})
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%d/%d/%d", count, maxID, latest.Unix()), nil
}

func (s *Store) loadStays(ctx context.Context, userID string) ([]Stay, time.Time, error) {
	var stays []Stay
	var anchor time.Time
	err := s.withRetry(ctx, func() error {
		var firstPoint *time.Time
		err := s.pool.QueryRow(ctx, `
			SELECT coalesce((SELECT min(timestamp) FROM gps_points WHERE user_id = $1::uuid),
			                (SELECT min(timestamp) FROM timeline_stays WHERE user_id = $1::uuid))`, userID).Scan(&firstPoint)
		if err != nil {
			return err
		}
		rows, err := s.pool.Query(ctx, `
			SELECT s.timestamp, s.stay_duration, s.location_name,
			       ST_Y(s.location), ST_X(s.location),
			       coalesce(f.city, g.city, ''), coalesce(f.country, g.country, ''),
			       coalesce(n.city, ''), coalesce(n.country, '')
			  FROM timeline_stays s
			  LEFT JOIN favorite_locations f ON f.id = s.favorite_id
			  LEFT JOIN reverse_geocoding_location g ON g.id = s.geocoding_id
			  -- Same lookup as GeoPulse's GeoNames normalization (nearest city within 50 km);
			  -- the bounding box lets it use the (latitude, longitude) index.
			  LEFT JOIN LATERAL (
			       SELECT gc.name AS city, coalesce(gct.country_name, gc.country_code) AS country
			         FROM geonames_city gc
			         LEFT JOIN geonames_country gct ON gct.iso_alpha2 = gc.country_code
			        WHERE gc.latitude BETWEEN ST_Y(s.location) - 0.45 AND ST_Y(s.location) + 0.45
			          AND gc.longitude BETWEEN ST_X(s.location) - 0.45 / greatest(cos(radians(ST_Y(s.location))), 0.01)
			                               AND ST_X(s.location) + 0.45 / greatest(cos(radians(ST_Y(s.location))), 0.01)
			          AND ST_DistanceSphere(ST_SetSRID(ST_MakePoint(gc.longitude, gc.latitude), 4326), s.location) <= 50000
			        ORDER BY ST_DistanceSphere(ST_SetSRID(ST_MakePoint(gc.longitude, gc.latitude), 4326), s.location),
			                 coalesce(gc.population, 0) DESC
			        LIMIT 1) n ON true
			 WHERE s.user_id = $1::uuid
			 ORDER BY s.timestamp`, userID)
		if err != nil {
			return err
		}
		stays = stays[:0]
		for rows.Next() {
			var stay Stay
			var seconds int64
			if err := rows.Scan(&stay.Start, &seconds, &stay.Name, &stay.Lat, &stay.Lon, &stay.City, &stay.Country, &stay.GeoNamesCity, &stay.GeoNamesCountry); err != nil {
				rows.Close()
				return err
			}
			stay.Duration = time.Duration(seconds) * time.Second
			stays = append(stays, stay)
		}
		if err := rows.Err(); err != nil {
			return err
		}
		if firstPoint != nil {
			anchor = *firstPoint
			AssignKeys(stays, anchor)
		}
		return nil
	})
	return stays, anchor, err
}

// AssignKeys sets each stay's Key relative to anchor, the user's first GPS point.
func AssignKeys(stays []Stay, anchor time.Time) {
	for i := range stays {
		stays[i].Key = int64(stays[i].Start.Sub(anchor) / time.Second)
	}
}

// Week groups stays into weeks counted from the first GPS point. Libraries balance photo and
// memo reuse within a week, so a rebuilt stay can only change choices in its own week.
func (s Stay) Week() int64 {
	const week = 7 * 24 * 60 * 60
	if s.Key < 0 {
		return (s.Key - week + 1) / week
	}
	return s.Key / week
}

// withRetry retries once, because the reset script terminates every connection when it
// swaps the databases and the pool only learns about that on the next query.
func (s *Store) withRetry(ctx context.Context, fn func() error) error {
	err := fn()
	if err == nil || errors.Is(err, pgx.ErrNoRows) || ctx.Err() != nil {
		return err
	}
	time.Sleep(200 * time.Millisecond)
	return fn()
}
