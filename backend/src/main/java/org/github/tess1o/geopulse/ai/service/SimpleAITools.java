package org.github.tess1o.geopulse.ai.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;

import java.time.LocalDate;

import static org.github.tess1o.geopulse.ai.service.AIToolArguments.zone;

@Slf4j
@ApplicationScoped
public class SimpleAITools {

    private final CurrentUserService currentUserService;

    @Inject
    public SimpleAITools(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    /** Today in the signed-in user's timezone, so relative dates ("yesterday") match the user's calendar. */
    public String getTodayDate() {
        LocalDate today = LocalDate.now(zone(currentUserService.getCurrentUser().getTimezone()));
        log.info("🔧 AI TOOL EXECUTED: getTodayDate() - returning: {}", today);
        return "Today's date is: " + today.toString() + " (current year is " + today.getYear() + ")";
    }
}
