// Package catalog holds the demo photo set baked into the fake Immich binary.
// The files are produced by scripts/fetch-photos.py (CC0 / public domain only).
package catalog

import (
	"embed"
	"encoding/json"
	"fmt"
)

//go:embed photos.json photos/*.jpg
var files embed.FS

type Photo struct {
	ID        string `json:"id"`
	Region    string `json:"region"` // "kyiv", "london", "new-york" or "" for photos that fit anywhere
	Category  string `json:"category"`
	Caption   string `json:"caption"`
	TimeOfDay string `json:"timeOfDay"` // "morning", "evening" or "" for any time
	Width     int    `json:"width"`
	Height    int    `json:"height"`
	License   string `json:"license"`
	Author    string `json:"author"`
	SourceURL string `json:"sourceUrl"`
	SizeBytes int    `json:"-"`
}

// Photos returns the catalog in a stable order.
func Photos() ([]Photo, error) {
	raw, err := files.ReadFile("photos.json")
	if err != nil {
		return nil, err
	}
	var photos []Photo
	if err := json.Unmarshal(raw, &photos); err != nil {
		return nil, fmt.Errorf("parse photos.json: %w", err)
	}
	for i := range photos {
		data, err := files.ReadFile("photos/" + photos[i].ID + ".jpg")
		if err != nil {
			return nil, fmt.Errorf("photo %s: %w", photos[i].ID, err)
		}
		photos[i].SizeBytes = len(data)
	}
	return photos, nil
}

// Preview returns the large rendition, which is also served as the "original".
func Preview(id string) ([]byte, error) {
	return files.ReadFile("photos/" + id + ".jpg")
}

func Thumbnail(id string) ([]byte, error) {
	return files.ReadFile("photos/" + id + ".thumb.jpg")
}
