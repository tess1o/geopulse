package main

import (
	"math"
	"math/rand/v2"
	"regexp"
	"slices"
	"sort"
	"strings"
	"time"

	"github.com/tess1o/geopulse/demo-integrations/internal/demodata"
)

const maxMemosPerDay = 2

var memoChance = map[string]float64{
	demodata.CategoryCoffee:     0.30,
	demodata.CategoryRestaurant: 0.30,
	demodata.CategoryFood:       0.20,
	demodata.CategoryPark:       0.30,
	demodata.CategoryTransit:    0.12,
	demodata.CategoryHome:       0.05,
	demodata.CategoryWork:       0.06,
	demodata.CategoryUnknown:    0.10,
}

const (
	coffee     = demodata.CategoryCoffee
	restaurant = demodata.CategoryRestaurant
	food       = demodata.CategoryFood
	park       = demodata.CategoryPark
	transit    = demodata.CategoryTransit
	home       = demodata.CategoryHome
	work       = demodata.CategoryWork
	unknown    = demodata.CategoryUnknown
)

// memoTemplate is one memo line. {place} is replaced with the stay name, so those lines are
// only used when the stay has a real name. Region-specific lines only appear in that city.
type memoTemplate struct {
	text     string
	fits     []string // place categories the line makes sense at
	region   string
	fromHour int // local hours the line makes sense at, [fromHour, toHour)
	toHour   int
}

func line(text string, fits ...string) memoTemplate {
	return memoTemplate{text: text, fits: fits, fromHour: 0, toHour: 24}
}

func (t memoTemplate) in(region string) memoTemplate { t.region = region; return t }

func (t memoTemplate) hours(from, to int) memoTemplate { t.fromHour, t.toHour = from, to; return t }

var memoTemplates = []memoTemplate{
	line("Flat white at {place} — best one this week. #coffee", coffee),
	line("Quick coffee before the next thing. Window seat, good light. #coffee", coffee),
	line("Working from {place} for an hour, wifi is solid and nobody minds laptops. #coffee #remote", coffee),
	line("Note to self: the almond croissant here is worth the queue. #coffee #food", coffee, food),
	line("Tried the filter of the day, something Ethiopian and very fruity. Would order again. #coffee", coffee),

	line("Dinner at {place}. Order the special again next time. #food #restaurants", restaurant).hours(17, 24),
	line("Great little spot. Add {place} to the list for when friends visit. #restaurants", restaurant),
	line("Lunch with the team ran way too long and was very worth it. #food", restaurant, food).hours(11, 16),
	line("Booked a table for Friday, ask for the one by the window. #restaurants #plans", restaurant),

	line("Shopping list:\n- [x] bread\n- [x] tomatoes\n- [ ] olive oil\n- [ ] coffee beans\n#groceries", food),
	line("Market haul: strawberries are finally good again. #food", food),
	line("Picked up pastries for tomorrow's breakfast. #food", food, coffee),

	line("Long walk, forty minutes without looking at the phone. Needed that. #walk", park),
	line("Sat on a bench and read a few chapters. #walk #reading", park),
	line("So many dogs out today. #walk", park),
	line("Sun finally came out and half the city is outside. #walk", park, unknown).hours(10, 19),

	line("Train delayed ten minutes, catching up on podcasts. #commute", transit),
	line("Platform is packed. Leave earlier on Fridays. #commute", transit),
	line("Reading on the way, almost missed my stop. #commute #reading", transit),

	line("Idea: map every coffee place I've been to this year. #ideas", home, work),
	line("Weekend plan: long walk, call parents, finally fix the bike. #plans", home),
	line("Movie night list:\n- [ ] Past Lives\n- [ ] Perfect Days\n- [x] Aftersun\n#movies", home).hours(17, 24),

	line("Good focus day. First draft of the release notes is done. #work", work).hours(12, 24),
	line("Meeting notes: agree on scope first, then dates. #work", work),

	line("Come back here when it is less busy. #places", unknown),
	line("Found a quiet street to walk home through. #walk", unknown).hours(16, 24),
	line("Nice light this evening. #photo", unknown, park).hours(17, 24),

	line("Walked down to Podil, the river looks amazing in the evening. #kyiv #walk", unknown, park).in("kyiv").hours(17, 24),
	line("Chestnut trees everywhere on this street. #kyiv", unknown, park).in("kyiv"),
	line("Took the funicular just for fun. #kyiv", unknown, transit).in("kyiv"),
	line("Syrnyky for brunch, the classic. #kyiv #food", coffee, restaurant, food).in("kyiv").hours(9, 14),

	line("Top deck, front seat on the bus. Small wins. #london", transit, unknown).in("london"),
	line("Rain, sun, rain again within twenty minutes. Classic. #london #weather", unknown, park, transit).in("london"),
	line("Walked along the South Bank, so many people out. #london #walk", unknown, park).in("london"),
	line("Found a tiny bookshop in a side street. #london #reading", unknown).in("london"),

	line("Walked thirty blocks instead of taking the subway. #nyc #walk", unknown, park).in("new-york"),
	line("Bagel stop: everything bagel with scallion cream cheese. #nyc #food", coffee, food, restaurant).in("new-york").hours(7, 14),
	line("Sunset straight down the avenue between the buildings. #nyc #photo", unknown).in("new-york").hours(17, 21),
	line("Street fair on the way home, bought way too many dumplings. #nyc #food", unknown, food).in("new-york").hours(12, 21),
}

var hashtag = regexp.MustCompile(`#([\p{L}\p{N}_-]+)`)

type Memo struct {
	UID      string
	Owner    string // user ID
	Content  string
	Tags     []string
	Created  time.Time
	Location *time.Location // owner's timezone, for the public memo page
	Place    string
	Lat, Lon float64
}

type Library struct {
	Version string
	Memos   []*Memo // newest first
}

func buildLibrary(snapshot *demodata.Snapshot) *Library {
	user := snapshot.User
	library := &Library{Version: snapshot.Version}
	var uses map[string]int
	week := int64(math.MinInt64)
	perDay := map[string]int{}

	for _, stay := range snapshot.Stays {
		if stay.Week() != week {
			// Balancing restarts every week, so a rebuilt stay never ripples beyond its week.
			week, uses = stay.Week(), map[string]int{}
		}
		if stay.Duration < 15*time.Minute {
			continue
		}
		category := demodata.CategoryOf(stay.Name)
		region := demodata.RegionAt(stay.Lat, stay.Lon)
		rng := demodata.Rand("memos", user.ID, stay.Key)
		chance := memoChance[category]
		if category == demodata.CategoryUnknown && region != nil {
			chance = 0.14
		}
		if rng.Float64() >= chance {
			continue
		}
		from, to, ok := demodata.DaytimeWindow(rng, stay, user.Location, 5*time.Minute)
		if !ok {
			continue
		}
		day := demodata.LocalDay(from, user.Location)
		if perDay[day] >= maxMemosPerDay {
			continue
		}

		created := demodata.SpreadTimes(rng, from, to, 1)[0]
		template := pickTemplate(rng, uses, category, region, demodata.HasRealName(stay.Name), created.In(user.Location).Hour())
		if template == "" {
			continue
		}
		uses[template]++
		perDay[day]++

		place := stay.Name
		if !demodata.HasRealName(place) && region != nil {
			place = region.City
		}
		content := strings.ReplaceAll(template, "{place}", place)
		lat, lon := demodata.Jitter(rng, stay.Lat, stay.Lon, 20)
		library.Memos = append(library.Memos, &Memo{
			UID:      shortID(demodata.Rand("memo-uid", user.ID, stay.Key)),
			Owner:    user.ID,
			Content:  content,
			Tags:     tagsOf(content),
			Created:  created.UTC(),
			Location: user.Location,
			Place:    place,
			Lat:      lat,
			Lon:      lon,
		})
	}

	sort.SliceStable(library.Memos, func(i, j int) bool { return library.Memos[i].Created.After(library.Memos[j].Created) })
	return library
}

// pickTemplate picks a line that suits the place and hour, preferring this city's own
// lines away from named places and the least used lines overall.
func pickTemplate(rng *rand.Rand, uses map[string]int, category string, region *demodata.Region, hasName bool, hour int) string {
	weights := make([]float64, len(memoTemplates))
	for i, t := range memoTemplates {
		if !slices.Contains(t.fits, category) || hour < t.fromHour || hour >= t.toHour {
			continue
		}
		if !hasName && strings.Contains(t.text, "{place}") {
			continue
		}
		weight := 3.0
		if t.region != "" {
			if region == nil || t.region != region.Key {
				continue
			}
			if category == unknown {
				weight = 5
			}
		}
		weights[i] = weight / (1 + 2*float64(uses[t.text]))
	}
	index := demodata.Weighted(rng, weights)
	if index < 0 {
		return ""
	}
	return memoTemplates[index].text
}

// shortID looks like the 22-character ids Memos generates.
func shortID(rng *rand.Rand) string {
	const alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
	id := make([]byte, 22)
	for i := range id {
		id[i] = alphabet[rng.IntN(len(alphabet))]
	}
	return string(id)
}

func (m *Memo) LocalTime() time.Time { return m.Created.In(m.Location) }

func tagsOf(content string) []string {
	tags := []string{}
	for _, match := range hashtag.FindAllStringSubmatch(content, -1) {
		tags = append(tags, match[1])
	}
	return tags
}
