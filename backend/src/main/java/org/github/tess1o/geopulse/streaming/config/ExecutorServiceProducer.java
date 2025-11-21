package org.github.tess1o.geopulse.streaming.config;

import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
public class ExecutorServiceProducer {

    @Produces
    @ApplicationScoped
    @Identifier("timeline-processing")
    public ExecutorService timelineProcessingExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("timeline-", 0).factory()
        );
    }
}
