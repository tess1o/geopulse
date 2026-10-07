package main

import (
	"cmp"
	"fmt"
	"math"
	"math/rand/v2"
	"sort"
	"time"

	"github.com/tess1o/geopulse/demo-integrations/internal/catalog"
	"github.com/tess1o/geopulse/demo-integrations/internal/demodata"
)

const maxPhotosPerDay = 6

// photoChance is how likely a stay of each kind is to have photos at all.
var photoChance = map[string]float64{
	demodata.CategoryCoffee:     0.55,
	demodata.CategoryRestaurant: 0.55,
	demodata.CategoryFood:       0.45,
	demodata.CategoryPark:       0.50,
	demodata.CategoryTransit:    0.20,
	demodata.CategoryHome:       0.12,
	demodata.CategoryWork:       0.08,
	demodata.CategoryUnknown:    0.30,
}

// photoAffinity scores how well a photo category suits a place category.
// Photos from the stay's own city get a bonus in pickPhoto.
var photoAffinity = map[string]map[string]float64{
	demodata.CategoryCoffee:     {"coffee": 10, "food": 2, "street": 1},
	demodata.CategoryRestaurant: {"restaurant": 10, "food": 4},
	demodata.CategoryFood:       {"food": 10, "restaurant": 3, "coffee": 2},
	demodata.CategoryPark:       {"park": 10, "view": 2},
	demodata.CategoryTransit:    {"transit": 10, "street": 1},
	demodata.CategoryHome:       {"home": 10, "food": 2, "work": 1},
	demodata.CategoryWork:       {"work": 10, "coffee": 4},
	demodata.CategoryUnknown:    {"landmark": 8, "street": 8, "view": 8, "park": 3, "food": 2, "transit": 2, "coffee": 1},
}

type device struct {
	make, model, lens string
	fileName          func(t time.Time, rng *rand.Rand) string
}

var devices = []device{
	{"Google", "Pixel 8", "Pixel 8 back camera 6.9mm f/1.68", func(t time.Time, rng *rand.Rand) string {
		return fmt.Sprintf("PXL_%s%03d.jpg", t.Format("20060102_150405"), rng.IntN(1000))
	}},
	{"Apple", "iPhone 15 Pro", "iPhone 15 Pro back triple camera 6.765mm f/1.78", func(t time.Time, rng *rand.Rand) string {
		return fmt.Sprintf("IMG_%04d.JPG", 1000+rng.IntN(9000))
	}},
	{"samsung", "Galaxy S24", "", func(t time.Time, rng *rand.Rand) string {
		return t.Format("20060102_150405") + ".jpg"
	}},
}

type Asset struct {
	ID       string
	Photo    *catalog.Photo
	TakenAt  time.Time
	Lat, Lon float64
	City     string // what Immich shows: the GeoNames name when known
	State    string
	Country  string
	Region   string
	// Cities and Countries hold every spelling the photo's place is known by (GeoNames and
	// GeoPulse's own geocoding), so search filters match either.
	Cities    []string
	Countries []string
	Favorite  bool
	Rating    *int
	FileName  string
	Device    *device
	FNumber   float64
	ISO       int
	Exposure  string
	Focal     float64
}

type Album struct {
	ID     string
	Name   string
	Assets []*Asset
}

type Library struct {
	Version string
	Owner   demodata.User
	Assets  []*Asset // newest first, like Immich
	ByID    map[string]*Asset
	Albums  []*Album
}

func buildLibrary(snapshot *demodata.Snapshot, photos []catalog.Photo) *Library {
	user := snapshot.User
	library := &Library{Version: snapshot.Version, Owner: user, ByID: map[string]*Asset{}}
	userDevice := &devices[demodata.Rand("device", user.Email).IntN(len(devices))]
	var uses []int
	week := int64(math.MinInt64)
	perDay := map[string]int{}
	usedToday := map[string]map[int]bool{}

	for _, stay := range snapshot.Stays {
		if stay.Week() != week {
			// Balancing restarts every week, so a rebuilt stay never ripples beyond its week.
			week, uses = stay.Week(), make([]int, len(photos))
		}
		category := demodata.CategoryOf(stay.Name)
		minimum := 10 * time.Minute
		if category == demodata.CategoryUnknown {
			minimum = 20 * time.Minute
		}
		if stay.Duration < minimum {
			continue
		}
		rng := demodata.Rand("photos", user.ID, stay.Key)
		if rng.Float64() >= photoChance[category] {
			continue
		}
		from, to, ok := demodata.DaytimeWindow(rng, stay, user.Location, 8*time.Minute)
		if !ok {
			continue
		}
		day := demodata.LocalDay(from, user.Location)
		count := []int{1, 1, 1, 1, 1, 1, 2, 2, 2, 3}[rng.IntN(10)]
		count = min(count, maxPhotosPerDay-perDay[day], int(to.Sub(from)/(4*time.Minute)))
		if count <= 0 {
			continue
		}
		if usedToday[day] == nil {
			usedToday[day] = map[int]bool{}
		}

		region := demodata.RegionAt(stay.Lat, stay.Lon)
		for i, takenAt := range demodata.SpreadTimes(rng, from, to, count) {
			index := pickPhoto(rng, photos, uses, usedToday[day], category, region, takenAt.In(user.Location).Hour())
			if index < 0 {
				break
			}
			uses[index]++
			usedToday[day][index] = true
			perDay[day]++

			lat, lon := demodata.Jitter(rng, stay.Lat, stay.Lon, 30)
			asset := &Asset{
				ID:        demodata.StableUUID("asset", user.ID, stay.Key, i),
				Photo:     &photos[index],
				TakenAt:   takenAt.UTC(),
				Lat:       lat,
				Lon:       lon,
				City:      cmp.Or(stay.GeoNamesCity, stay.City),
				Country:   cmp.Or(stay.GeoNamesCountry, stay.Country),
				Cities:    []string{stay.GeoNamesCity, stay.City},
				Countries: []string{stay.GeoNamesCountry, stay.Country},
				Favorite:  rng.Float64() < 0.08,
				Device:    userDevice,
				FNumber:   []float64{1.7, 1.8, 2.2}[rng.IntN(3)],
				ISO:       []int{50, 64, 100, 160, 250, 400, 800}[rng.IntN(7)],
				Exposure:  []string{"1/60", "1/120", "1/250", "1/500", "1/1000"}[rng.IntN(5)],
				Focal:     []float64{6.9, 6.77, 2.22}[rng.IntN(3)],
			}
			if region != nil {
				asset.City = cmp.Or(asset.City, region.City)
				asset.Country = cmp.Or(asset.Country, region.Country)
				asset.State, asset.Region = region.State, region.Key
			}
			if rng.Float64() < 0.15 {
				rating := 4 + rng.IntN(2)
				asset.Rating = &rating
			}
			asset.FileName = userDevice.fileName(takenAt.In(user.Location), rng)
			library.Assets = append(library.Assets, asset)
			library.ByID[asset.ID] = asset
		}
	}

	sort.SliceStable(library.Assets, func(i, j int) bool { return library.Assets[i].TakenAt.After(library.Assets[j].TakenAt) })
	library.Albums = buildAlbums(user, library.Assets)
	return library
}

// pickPhoto prefers photos that suit the place, come from the same city, and have been
// used least, and never repeats a photo on the same day or shows a sunset at 9 a.m.
func pickPhoto(rng *rand.Rand, photos []catalog.Photo, uses []int, usedToday map[int]bool, category string, region *demodata.Region, localHour int) int {
	weights := make([]float64, len(photos))
	for i, photo := range photos {
		if usedToday[i] || !fitsHour(photo.TimeOfDay, localHour) {
			continue
		}
		if photo.Region != "" && (region == nil || photo.Region != region.Key) {
			continue
		}
		weight := photoAffinity[category][photo.Category]
		if region == nil && category == demodata.CategoryUnknown && photo.Region == "" {
			// Somewhere outside the demo cities: generic street and park shots only.
			weight = map[string]float64{"street": 6, "park": 4, "coffee": 2, "food": 2, "restaurant": 1}[photo.Category]
		}
		if photo.Region != "" {
			weight *= 1.4
		}
		weights[i] = weight / (1 + 1.5*float64(uses[i]))
	}
	return demodata.Weighted(rng, weights)
}

func fitsHour(timeOfDay string, hour int) bool {
	switch timeOfDay {
	case "morning":
		return hour >= 6 && hour < 11
	case "evening":
		return hour >= 17
	default:
		return true
	}
}

func buildAlbums(user demodata.User, assets []*Asset) []*Album {
	byName := map[string]*Album{}
	var order []string
	add := func(name string, asset *Asset) {
		album := byName[name]
		if album == nil {
			album = &Album{ID: demodata.StableUUID("album", user.ID, name), Name: name}
			byName[name] = album
			order = append(order, name)
		}
		album.Assets = append(album.Assets, asset)
	}
	regionAlbums := map[string]string{"kyiv": "Kyiv", "london": "London", "new-york": "New York"}
	for _, asset := range assets {
		if name, ok := regionAlbums[asset.Region]; ok {
			add(name, asset)
		}
		switch asset.Photo.Category {
		case "coffee", "food", "restaurant":
			add("Coffee & food", asset)
		case "park":
			add("Walks", asset)
		}
		if asset.Favorite {
			add("Favorites", asset)
		}
	}
	albums := make([]*Album, 0, len(order))
	for _, name := range order {
		albums = append(albums, byName[name])
	}
	sort.SliceStable(albums, func(i, j int) bool { return albums[i].Name < albums[j].Name })
	return albums
}
