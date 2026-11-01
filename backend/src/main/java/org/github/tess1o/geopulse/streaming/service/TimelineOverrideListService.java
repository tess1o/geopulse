package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineOverrideSummaryDTO;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapStayOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayNameOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripMovementOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripStaySplitOverrideRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** All of a user's manual timeline corrections, including those the timeline no longer shows. */
@ApplicationScoped
public class TimelineOverrideListService {

    @Inject
    TimelineTripMovementOverrideRepository movementOverrideRepository;

    @Inject
    TimelineTripStaySplitOverrideRepository staySplitOverrideRepository;

    @Inject
    TimelineDataGapStayOverrideRepository dataGapStayOverrideRepository;

    @Inject
    TimelineStayNameOverrideRepository stayNameOverrideRepository;

    @Inject
    StayNameOverrideService stayNameOverrideService;

    @Transactional
    public List<TimelineOverrideSummaryDTO> listOverrides(UUID userId) {
        List<TimelineOverrideSummaryDTO> overrides = new ArrayList<>();
        movementOverrideRepository.findByUserId(userId).forEach(override -> overrides.add(
                TimelineOverrideSummaryDTO.builder()
                        .type("MOVEMENT_TYPE")
                        .id(override.getId())
                        .startTime(override.getSourceTripTimestamp())
                        .endTime(override.getSourceTripTimestamp().plusSeconds(override.getSourceTripDurationSeconds()))
                        .applied(override.getTrip() != null)
                        .movementType(override.getMovementType())
                        .build()));
        staySplitOverrideRepository.findByUserId(userId).forEach(override -> overrides.add(
                TimelineOverrideSummaryDTO.builder()
                        .type("TRIP_SPLIT")
                        .id(override.getId())
                        .startTime(override.getStayStartTime())
                        .endTime(override.getStayEndTime())
                        .applied(override.getStay() != null)
                        .locationName(override.getStayLocationName())
                        .build()));
        dataGapStayOverrideRepository.findByUserId(userId).forEach(override -> overrides.add(
                TimelineOverrideSummaryDTO.builder()
                        .type("DATA_GAP_STAY")
                        .id(override.getId())
                        .startTime(override.getSourceGapStartTime())
                        .endTime(override.getSourceGapEndTime())
                        .applied(override.getStay() != null)
                        .locationName(override.getStay() != null
                                ? override.getStay().getLocationName()
                                : override.getSelectedLocationName())
                        .build()));
        stayNameOverrideRepository.findByUserId(userId).forEach(override -> overrides.add(
                TimelineOverrideSummaryDTO.builder()
                        .type("STAY_NAME")
                        .id(override.getId())
                        .startTime(override.getStayStartTime())
                        .endTime(override.getStayEndTime())
                        .applied(stayNameOverrideService.findStay(userId, override).isPresent())
                        .locationName(override.getLocationName())
                        .build()));
        overrides.sort(Comparator.comparing(TimelineOverrideSummaryDTO::startTime));
        return overrides;
    }
}
