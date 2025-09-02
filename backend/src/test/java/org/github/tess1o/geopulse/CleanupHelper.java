package org.github.tess1o.geopulse;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;

@ApplicationScoped
public class CleanupHelper {

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineStayRepository stayRepository;

    @Transactional
    public void cleanupTimeline() {
        this.stayRepository.deleteAll();
        this.tripRepository.deleteAll();
        this.dataGapRepository.deleteAll();
    }
}
