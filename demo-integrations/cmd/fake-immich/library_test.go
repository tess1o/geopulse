package main

import (
	"testing"
	"time"

	"github.com/tess1o/geopulse/demo-integrations/internal/catalog"
	"github.com/tess1o/geopulse/demo-integrations/internal/demodata"
)

func testLibrary(t *testing.T, location *time.Location) (*demodata.Snapshot, *Library, []catalog.Photo) {
	t.Helper()
	photos, err := catalog.Photos()
	if err != nil {
		t.Fatal(err)
	}
	snapshot := demodata.SampleSnapshot(location, time.Date(2026, 3, 1, 0, 0, 0, 0, location), 60)
	return snapshot, buildLibrary(snapshot, photos), photos
}

func TestLibraryIsDeterministic(t *testing.T) {
	kyiv, _ := time.LoadLocation("Europe/Kyiv")
	snapshot, first, photos := testLibrary(t, kyiv)
	second := buildLibrary(snapshot, photos)

	if len(first.Assets) < 60 {
		t.Fatalf("expected a few photos per day over 60 days, got %d", len(first.Assets))
	}
	if len(first.Assets) != len(second.Assets) {
		t.Fatalf("asset counts differ: %d vs %d", len(first.Assets), len(second.Assets))
	}
	for i := range first.Assets {
		a, b := first.Assets[i], second.Assets[i]
		if a.ID != b.ID || a.Photo.ID != b.Photo.ID || !a.TakenAt.Equal(b.TakenAt) {
			t.Fatalf("asset %d differs between builds", i)
		}
	}
}

func TestPhotosAreTakenDuringStaysAtPlausibleHours(t *testing.T) {
	kyiv, _ := time.LoadLocation("Europe/Kyiv")
	snapshot, library, _ := testLibrary(t, kyiv)

	perDay := map[string]int{}
	for _, asset := range library.Assets {
		inStay := false
		for _, stay := range snapshot.Stays {
			if !asset.TakenAt.Before(stay.Start) && !asset.TakenAt.After(stay.Start.Add(stay.Duration)) {
				inStay = true
				break
			}
		}
		if !inStay {
			t.Errorf("%s at %s is outside every stay", asset.Photo.ID, asset.TakenAt)
		}
		local := asset.TakenAt.In(kyiv)
		if local.Hour() < 7 || local.Hour() >= 23 {
			t.Errorf("%s taken at %s local time", asset.Photo.ID, local.Format("15:04"))
		}
		if !fitsHour(asset.Photo.TimeOfDay, local.Hour()) {
			t.Errorf("%s (%s) taken at %s", asset.Photo.ID, asset.Photo.TimeOfDay, local.Format("15:04"))
		}
		if asset.Photo.Region != "" && asset.Photo.Region != "kyiv" {
			t.Errorf("%s from another city used in Kyiv", asset.Photo.ID)
		}
		perDay[demodata.LocalDay(asset.TakenAt, kyiv)]++
	}
	for day, count := range perDay {
		if count > maxPhotosPerDay {
			t.Errorf("%d photos on %s, cap is %d", count, day, maxPhotosPerDay)
		}
	}
}

// The reset script shifts the demo data by whole days. Photos must follow: same ids, same
// pictures, timestamps moved by exactly the same amount.
func TestPhotosFollowTheDemoDateShift(t *testing.T) {
	snapshot, before, photos := testLibrary(t, time.UTC)
	after := buildLibrary(snapshot.Shifted(37), photos)

	if len(before.Assets) != len(after.Assets) {
		t.Fatalf("asset counts differ after shift: %d vs %d", len(before.Assets), len(after.Assets))
	}
	for i := range before.Assets {
		a, b := before.Assets[i], after.Assets[i]
		if a.ID != b.ID || a.Photo.ID != b.Photo.ID {
			t.Fatalf("asset %d changed identity after the shift", i)
		}
		if got := b.TakenAt.Sub(a.TakenAt); got != 37*24*time.Hour {
			t.Fatalf("asset %s moved by %s, want 37 days", a.ID, got)
		}
	}
}

func TestPlaceMatches(t *testing.T) {
	tests := []struct {
		filter string
		names  []string
		region string
		isCity bool
		want   bool
	}{
		{"", []string{"Київ"}, "kyiv", true, true},
		{"Київ", []string{"Київ"}, "kyiv", true, true},
		{"Kyiv", []string{"Київ"}, "kyiv", true, true},
		{"Kiev", []string{"Київ"}, "kyiv", true, true},
		{"London", []string{"Київ"}, "kyiv", true, false},
		{"Podil", []string{"Київ"}, "kyiv", true, true},
		{"Lviv", []string{"Lviv"}, "", true, true},
		{"Odesa", []string{"Lviv"}, "", true, false},
		{"Lviv", []string{"", ""}, "", true, true},
		// GeoPulse sends GeoNames names for coordinate searches and its own names otherwise.
		{"Ternopil", []string{"Ternopil", "Тернопіль"}, "", true, true},
		{"Тернопіль", []string{"Ternopil", "Тернопіль"}, "", true, true},
		{"Ternopil", []string{"", "Тернопіль"}, "", true, false},
		{"Ukraine", []string{"Україна"}, "kyiv", false, true},
		{"Ukraine", []string{"", "Україна"}, "", false, true},
		{"Poland", []string{"Ukraine", "Україна"}, "", false, false},
		{"United Kingdom", []string{"Україна"}, "kyiv", false, false},
		{"France", []string{"Україна"}, "kyiv", false, false},
	}
	for _, tt := range tests {
		if got := placeMatches(tt.filter, tt.names, tt.region, tt.isCity); got != tt.want {
			t.Errorf("placeMatches(%q, %q, %q, %v) = %v, want %v", tt.filter, tt.names, tt.region, tt.isCity, got, tt.want)
		}
	}
}

// The timeline job replaces stays when it rebuilds them (new ids, often a longer latest stay).
// Photos elsewhere in the timeline must not change.
func TestTimelineRebuildOnlyAffectsTheRebuiltStaysWeek(t *testing.T) {
	kyiv, _ := time.LoadLocation("Europe/Kyiv")
	snapshot, before, photos := testLibrary(t, kyiv)

	rebuilt := *snapshot
	rebuilt.Stays = append([]demodata.Stay(nil), snapshot.Stays...)
	middle := len(rebuilt.Stays) / 2
	rebuilt.Stays[middle].Duration += 25 * time.Minute
	rebuilt.Stays[len(rebuilt.Stays)-1].Duration += 40 * time.Minute
	after := buildLibrary(&rebuilt, photos)

	touchedWeeks := map[int64]bool{rebuilt.Stays[middle].Week(): true, rebuilt.Stays[len(rebuilt.Stays)-1].Week(): true}
	compared := 0
	for _, stay := range snapshot.Stays {
		if touchedWeeks[stay.Week()] {
			continue
		}
		for _, asset := range before.Assets {
			if asset.TakenAt.Before(stay.Start) || asset.TakenAt.After(stay.Start.Add(stay.Duration)) {
				continue
			}
			other := after.ByID[asset.ID]
			if other == nil || other.Photo.ID != asset.Photo.ID || !other.TakenAt.Equal(asset.TakenAt) {
				t.Fatalf("photo %s in an untouched week changed after a rebuild", asset.ID)
			}
			compared++
		}
	}
	if compared < 30 {
		t.Fatalf("compared only %d photos", compared)
	}
}
