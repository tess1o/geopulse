package demodata

import "time"

// SampleSnapshot builds a timeline that looks like a demo persona's: a regular weekday
// routine in Kyiv repeated for the given number of days. Tests in both servers use it.
func SampleSnapshot(location *time.Location, firstDay time.Time, days int) *Snapshot {
	type visit struct {
		hour, minutes int
		name          string
		lat, lon      float64
	}
	routine := []visit{
		{0, 510, "Home", 50.4547, 30.5238},
		{8*60 + 45, 40, "One Love espresso bar", 50.4470, 30.5160},
		{9*60 + 45, 480, "Office", 50.4420, 30.5100},
		{18*60 + 30, 75, "Маріїнський парк", 50.4460, 30.5390},
		{20 * 60, 90, "Location 3", 50.4650, 30.5150},
		{21*60 + 45, 135, "Home", 50.4547, 30.5238},
	}
	snapshot := &Snapshot{
		User:    User{ID: "11111111-1111-1111-1111-111111111111", Email: "kyiv@demo.geopulse.cc", Location: location},
		Version: "test",
	}
	for day := 0; day < days; day++ {
		midnight := time.Date(firstDay.Year(), firstDay.Month(), firstDay.Day()+day, 0, 0, 0, 0, location)
		for _, v := range routine {
			snapshot.Stays = append(snapshot.Stays, Stay{
				Start:    midnight.Add(time.Duration(v.hour) * time.Minute),
				Duration: time.Duration(v.minutes) * time.Minute,
				Name:     v.name,
				Lat:      v.lat,
				Lon:      v.lon,
				City:     "Київ",
				Country:  "Україна",
			})
		}
	}
	// The first GPS point of the day the data starts, as in the demo database.
	snapshot.Anchor = snapshot.Stays[0].Start.Add(-90 * time.Minute)
	AssignKeys(snapshot.Stays, snapshot.Anchor)
	return snapshot
}

// Shifted returns a copy with every stay moved by whole days, like the demo reset script does.
func (s *Snapshot) Shifted(days int) *Snapshot {
	shifted := *s
	shifted.Stays = make([]Stay, len(s.Stays))
	for i, stay := range s.Stays {
		stay.Start = stay.Start.Add(time.Duration(days) * 24 * time.Hour)
		stay.Key = 0
		shifted.Stays[i] = stay
	}
	shifted.Anchor = s.Anchor.Add(time.Duration(days) * 24 * time.Hour)
	AssignKeys(shifted.Stays, shifted.Anchor)
	shifted.Version = s.Version + "+shifted"
	return &shifted
}
