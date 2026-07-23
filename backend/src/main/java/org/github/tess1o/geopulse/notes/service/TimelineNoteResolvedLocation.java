package org.github.tess1o.geopulse.notes.service;

import org.github.tess1o.geopulse.notes.model.NoteLocationSource;

record TimelineNoteResolvedLocation(Double latitude, Double longitude, String placeholder, NoteLocationSource source) {
}
