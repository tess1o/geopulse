package main

import (
	"encoding/json"
	"fmt"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/tess1o/geopulse/demo-integrations/internal/demodata"
)

func sampleLibrary(location *time.Location) (*demodata.Snapshot, *Library) {
	snapshot := demodata.SampleSnapshot(location, time.Date(2026, 3, 1, 0, 0, 0, 0, location), 60)
	return snapshot, buildLibrary(snapshot)
}

func TestMemosAreWrittenDuringStays(t *testing.T) {
	kyiv, _ := time.LoadLocation("Europe/Kyiv")
	snapshot, library := sampleLibrary(kyiv)
	if len(library.Memos) < 15 {
		t.Fatalf("expected some memos over 60 days, got %d", len(library.Memos))
	}
	perDay := map[string]int{}
	for _, memo := range library.Memos {
		inStay := false
		for _, stay := range snapshot.Stays {
			if !memo.Created.Before(stay.Start) && !memo.Created.After(stay.Start.Add(stay.Duration)) {
				inStay = true
				break
			}
		}
		if !inStay {
			t.Errorf("memo %q at %s is outside every stay", memo.Content, memo.Created)
		}
		if strings.Contains(memo.Content, "{place}") || strings.Contains(memo.Content, "Location 3") {
			t.Errorf("memo leaks a placeholder: %q", memo.Content)
		}
		if strings.Contains(memo.Content, "#london") || strings.Contains(memo.Content, "#nyc") {
			t.Errorf("memo from another city: %q", memo.Content)
		}
		perDay[demodata.LocalDay(memo.Created, kyiv)]++
	}
	for day, count := range perDay {
		if count > maxMemosPerDay {
			t.Errorf("%d memos on %s, cap is %d", count, day, maxMemosPerDay)
		}
	}
}

func TestMemosFollowTheDemoDateShift(t *testing.T) {
	snapshot, before := sampleLibrary(time.UTC)
	after := buildLibrary(snapshot.Shifted(12))
	if len(before.Memos) != len(after.Memos) {
		t.Fatalf("memo counts differ after shift: %d vs %d", len(before.Memos), len(after.Memos))
	}
	for i := range before.Memos {
		a, b := before.Memos[i], after.Memos[i]
		if a.UID != b.UID || a.Content != b.Content || b.Created.Sub(a.Created) != 12*24*time.Hour {
			t.Fatalf("memo %d did not move with the shift: %+v -> %+v", i, a, b)
		}
	}
}

// listMemos must honour the exact filter string GeoPulse's MemosClient builds.
func TestListMemosUnderstandsGeoPulseFilter(t *testing.T) {
	_, library := sampleLibrary(time.UTC)
	from := library.Memos[len(library.Memos)/2].Created.Add(-72 * time.Hour)
	to := from.Add(14 * 24 * time.Hour)

	expected := 0
	for _, memo := range library.Memos {
		if !memo.Created.Before(from.Truncate(time.Second)) && !memo.Created.After(to) && containsTag(memo.Tags, "walk", "coffee") {
			expected++
		}
	}
	if expected < 2 {
		t.Fatalf("sample data too thin for this test: %d matching memos", expected)
	}

	filter := fmt.Sprintf(`created_ts >= timestamp(%d) && created_ts <= timestamp(%d) && ("walk" in tags || "coffee" in tags)`, from.Unix(), to.Unix())
	var collected []map[string]any
	pageToken := ""
	for page := 0; page < 20; page++ {
		query := url.Values{"pageSize": {"1"}, "filter": {filter}, "orderBy": {"create_time desc"}, "pageToken": {pageToken}}
		recorder := httptest.NewRecorder()
		(&server{}).listMemos(recorder, httptest.NewRequest("GET", "/api/v1/memos?"+query.Encode(), nil), library)
		var response struct {
			Memos         []map[string]any `json:"memos"`
			NextPageToken string           `json:"nextPageToken"`
		}
		if err := json.Unmarshal(recorder.Body.Bytes(), &response); err != nil {
			t.Fatal(err)
		}
		collected = append(collected, response.Memos...)
		if response.NextPageToken == "" {
			break
		}
		pageToken = response.NextPageToken
	}
	if len(collected) != expected {
		t.Fatalf("got %d memos across pages, want %d", len(collected), expected)
	}
	for i := 1; i < len(collected); i++ {
		if collected[i-1]["createTime"].(string) < collected[i]["createTime"].(string) {
			t.Fatalf("memos not in create_time desc order")
		}
	}
}

func containsTag(tags []string, wanted ...string) bool {
	for _, tag := range tags {
		for _, w := range wanted {
			if tag == w {
				return true
			}
		}
	}
	return false
}

func TestTimelineRebuildKeepsOtherMemos(t *testing.T) {
	snapshot, before := sampleLibrary(time.UTC)
	rebuilt := *snapshot
	rebuilt.Stays = append([]demodata.Stay(nil), snapshot.Stays...)
	last := len(rebuilt.Stays) - 1
	rebuilt.Stays[last].Duration += 40 * time.Minute
	after := buildLibrary(&rebuilt)

	afterByUID := map[string]*Memo{}
	for _, memo := range after.Memos {
		afterByUID[memo.UID] = memo
	}
	lastWeek := rebuilt.Stays[last].Week()
	for _, memo := range before.Memos {
		if memo.Created.After(snapshot.Stays[last].Start.Add(-8 * 24 * time.Hour)) {
			continue // the rebuilt stay's week may change
		}
		other := afterByUID[memo.UID]
		if other == nil || other.Content != memo.Content || !other.Created.Equal(memo.Created) {
			t.Fatalf("memo %s changed after rebuilding a stay in week %d", memo.UID, lastWeek)
		}
	}
}
