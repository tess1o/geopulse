package org.github.tess1o.geopulse.admin.service;

import io.quarkus.runtime.Quarkus;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BackendExitCoordinator {
    public void restartRequested() { Quarkus.asyncExit(0); }
}
