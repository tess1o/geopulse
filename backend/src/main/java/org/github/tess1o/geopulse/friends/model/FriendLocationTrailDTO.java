package org.github.tess1o.geopulse.friends.model;

import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;

import java.util.List;
import java.util.UUID;

public record FriendLocationTrailDTO(UUID friendId, List<GpsPointPathPointDTO> points) {
}
