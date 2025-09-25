package org.github.tess1o.geopulse.ai.service;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

@Slf4j
public class SimpleAITools {

    @Tool("MANDATORY: Call this first when user mentions relative dates like 'September', 'last month', 'this year' - returns current date with year")
    public String getTodayDate() {
        LocalDate today = LocalDate.now();
        log.info("🔧 AI TOOL EXECUTED: getTodayDate() - returning: {}", today);
        return "Today's date is: " + today.toString() + " (current year is " + today.getYear() + ")";
    }
}
