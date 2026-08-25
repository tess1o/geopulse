package org.github.tess1o.geopulse.mapmatching.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;

/**
 * Establishes the CDI request context required by Hibernate ORM while the
 * map-matching worker runs on its dedicated virtual-thread executor.
 */
@ApplicationScoped
public class MapMatchingWorkerExecution {

    @ActivateRequestContext
    public void run(Runnable work) {
        work.run();
    }
}
