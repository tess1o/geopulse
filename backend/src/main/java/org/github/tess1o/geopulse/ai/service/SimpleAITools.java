package org.github.tess1o.geopulse.ai.service;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

@Slf4j
public class SimpleAITools {

    @Tool("Returns today's date with year. Call this first when user mentions relative dates like 'this month', 'last week', 'yesterday', or 'this year'.")
    public String getTodayDate() {
        LocalDate today = LocalDate.now();
        log.info("🔧 AI TOOL EXECUTED: getTodayDate() - returning: {}", today);
        return "Today's date is: " + today.toString() + " (current year is " + today.getYear() + ")";
    }
}
