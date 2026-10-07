package demodata

import (
	"crypto/sha1"
	"encoding/binary"
	"fmt"
	"math"
	"math/rand/v2"
	"regexp"
	"strings"
	"time"
	"unicode"
)

// Region is one of the demo cities that has its own photos and memo lines.
type Region struct {
	Key     string
	City    string
	State   string
	Country string
	Lat     float64
	Lon     float64
	RadiusM float64
}

var regions = []Region{
	{Key: "kyiv", City: "Kyiv", State: "Kyiv City", Country: "Ukraine", Lat: 50.4501, Lon: 30.5234, RadiusM: 40_000},
	{Key: "london", City: "London", State: "England", Country: "United Kingdom", Lat: 51.5074, Lon: -0.1278, RadiusM: 45_000},
	{Key: "new-york", City: "New York City", State: "New York", Country: "United States of America", Lat: 40.7128, Lon: -74.0060, RadiusM: 45_000},
}

// RegionAt returns the demo city around a coordinate, if any.
func RegionAt(lat, lon float64) *Region {
	for i := range regions {
		if DistanceMeters(lat, lon, regions[i].Lat, regions[i].Lon) <= regions[i].RadiusM {
			return &regions[i]
		}
	}
	return nil
}

func DistanceMeters(lat1, lon1, lat2, lon2 float64) float64 {
	const earthRadius = 6_371_000.0
	dLat := (lat2 - lat1) * math.Pi / 180
	dLon := (lon2 - lon1) * math.Pi / 180
	a := math.Sin(dLat/2)*math.Sin(dLat/2) +
		math.Cos(lat1*math.Pi/180)*math.Cos(lat2*math.Pi/180)*math.Sin(dLon/2)*math.Sin(dLon/2)
	return 2 * earthRadius * math.Asin(math.Sqrt(a))
}

// Place categories inferred from a stay's name.
const (
	CategoryCoffee     = "coffee"
	CategoryRestaurant = "restaurant"
	CategoryFood       = "food"
	CategoryPark       = "park"
	CategoryTransit    = "transit"
	CategoryHome       = "home"
	CategoryWork       = "work"
	CategoryUnknown    = "unknown"
)

// Keywords match whole words; a trailing "*" makes it a prefix ("кав*" matches "кав'ярня").
// Multi-word phrases are matched against the whole name.
var categoryKeywords = []struct {
	category string
	words    []string
}{
	{CategoryHome, []string{"home", "дім", "дом", "домівка"}},
	{CategoryWork, []string{"work", "office", "офіс", "робота", "coworking", "wework"}},
	{CategoryCoffee, []string{"coffee", "café", "cafe", "кав*", "кафе", "espresso", "starbucks", "costa", "pret", "roaster*", "кофе*"}},
	{CategoryRestaurant, []string{"restaurant*", "ресторан*", "bar", "pub", "паб", "bistro", "brasserie", "tavern", "trattoria", "pizzeri*", "піцері*", "grill", "steakhouse", "бар"}},
	{CategoryFood, []string{"bakery", "пекарн*", "deli", "diner", "market", "ринок", "базар", "supermarket", "супермаркет", "grocery", "сільпо", "атб", "novus", "tesco", "sainsbury*", "waitrose", "whole foods", "trader joe*", "bagel*", "food"}},
	{CategoryPark, []string{"park", "парк*", "garden*", "сад", "сквер*", "heath", "common", "botanical", "ботанічн*", "beach", "пляж"}},
	{CategoryTransit, []string{"station", "станці*", "вокзал*", "метро", "subway", "underground", "airport", "аеропорт*", "terminal", "railway", "зупинк*", "tube"}},
}

var placeholderName = regexp.MustCompile(`(?i)^(location|place|unknown)\s*\d*$`)

// CategoryOf guesses what kind of place a stay is from its name.
func CategoryOf(name string) string {
	lower := strings.ToLower(name)
	tokens := strings.FieldsFunc(lower, func(r rune) bool {
		return !unicode.IsLetter(r) && !unicode.IsDigit(r) && r != '\''
	})
	for _, entry := range categoryKeywords {
		for _, word := range entry.words {
			if strings.Contains(word, " ") {
				if strings.Contains(lower, word) {
					return entry.category
				}
				continue
			}
			prefix, isPrefix := strings.CutSuffix(word, "*")
			for _, token := range tokens {
				if token == word || (isPrefix && strings.HasPrefix(token, prefix)) {
					return entry.category
				}
			}
		}
	}
	return CategoryUnknown
}

// HasRealName reports whether a stay name is worth quoting in a memo ("Location 3" is not).
func HasRealName(name string) bool {
	name = strings.TrimSpace(name)
	return name != "" && !placeholderName.MatchString(name)
}

// Rand returns a generator that always yields the same sequence for the same parts, so a
// stay gets the same photos and memos on every request and after every reset.
func Rand(parts ...any) *rand.Rand {
	sum := sha1.Sum([]byte(fmt.Sprint(parts...)))
	return rand.New(rand.NewPCG(binary.BigEndian.Uint64(sum[0:8]), binary.BigEndian.Uint64(sum[8:16])))
}

// StableUUID formats a hash of parts as a version-5-style UUID.
func StableUUID(parts ...any) string {
	sum := sha1.Sum([]byte(fmt.Sprint(parts...)))
	sum[6] = (sum[6] & 0x0f) | 0x50
	sum[8] = (sum[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", sum[0:4], sum[4:6], sum[6:8], sum[8:10], sum[10:16])
}

// Jitter moves a point up to maxMeters in a random direction.
func Jitter(rng *rand.Rand, lat, lon, maxMeters float64) (float64, float64) {
	distance := math.Sqrt(rng.Float64()) * maxMeters
	bearing := rng.Float64() * 2 * math.Pi
	dLat := distance * math.Cos(bearing) / 111_320
	dLon := distance * math.Sin(bearing) / (111_320 * math.Cos(lat*math.Pi/180))
	return lat + dLat, lon + dLon
}

// DaytimeWindow returns the part of a stay that falls between 07:30 and 23:00 local time,
// trimmed by a few minutes at each end. Nobody takes photos at 4 a.m. at home.
// Overnight stays may cover several days; one of them is picked at random.
func DaytimeWindow(rng *rand.Rand, stay Stay, location *time.Location, minLength time.Duration) (time.Time, time.Time, bool) {
	start := stay.Start.Add(3 * time.Minute)
	end := stay.Start.Add(stay.Duration - 3*time.Minute)
	if !end.After(start) {
		return time.Time{}, time.Time{}, false
	}

	type window struct{ from, to time.Time }
	var windows []window
	day := time.Date(start.In(location).Year(), start.In(location).Month(), start.In(location).Day(), 0, 0, 0, 0, location)
	for ; day.Before(end); day = day.AddDate(0, 0, 1) {
		from := maxTime(start, day.Add(7*time.Hour+30*time.Minute))
		to := minTime(end, day.Add(23*time.Hour))
		if to.Sub(from) >= minLength {
			windows = append(windows, window{from, to})
		}
	}
	if len(windows) == 0 {
		return time.Time{}, time.Time{}, false
	}
	chosen := windows[rng.IntN(len(windows))]
	return chosen.from, chosen.to, true
}

// SpreadTimes places n moments across [from, to] with a little randomness.
func SpreadTimes(rng *rand.Rand, from, to time.Time, n int) []time.Time {
	span := to.Sub(from)
	slot := span / time.Duration(n)
	times := make([]time.Time, n)
	for i := range times {
		offset := slot*time.Duration(i) + time.Duration(float64(slot)*(0.2+0.6*rng.Float64()))
		times[i] = from.Add(offset).Truncate(time.Second)
	}
	return times
}

func maxTime(a, b time.Time) time.Time {
	if a.After(b) {
		return a
	}
	return b
}

func minTime(a, b time.Time) time.Time {
	if a.Before(b) {
		return a
	}
	return b
}

// Weighted picks an index from weights; it returns -1 when all weights are zero.
func Weighted(rng *rand.Rand, weights []float64) int {
	total := 0.0
	for _, weight := range weights {
		total += weight
	}
	if total <= 0 {
		return -1
	}
	target := rng.Float64() * total
	for i, weight := range weights {
		target -= weight
		if target < 0 {
			return i
		}
	}
	return len(weights) - 1
}

// LocalDay is the calendar day of t in the user's timezone, used for per-day caps.
func LocalDay(t time.Time, location *time.Location) string {
	return t.In(location).Format("2006-01-02")
}
