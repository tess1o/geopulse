package org.github.tess1o.geopulse.mcp;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.ai.model.AIPeriodDigestDTO;
import org.github.tess1o.geopulse.ai.model.AIStatisticsDTO;
import org.github.tess1o.geopulse.ai.service.AIInsightsTools;
import org.github.tess1o.geopulse.coverage.model.CoverageSummary;
import org.github.tess1o.geopulse.insight.model.JourneyInsights;

@ApplicationScoped
@RolesAllowed({"USER", "ADMIN"})
public class GeoPulseInsightsMcpTools {

    private final AIInsightsTools insightsTools;

    public GeoPulseInsightsMcpTools(AIInsightsTools insightsTools) {
        this.insightsTools = insightsTools;
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Gets the digest of a calendar month or year in the user's timezone: key metrics, comparison with the previous period, highlights, top places and milestones. Prefer it for month or year summaries.")
    AIPeriodDigestDTO getPeriodDigest(
            @ToolArg(description = "Year, 2000 to 2100.") Integer year,
            @ToolArg(description = "Month 1 to 12. Omit for the whole year.", required = false) Integer month) {
        return McpToolCalls.execute(() -> insightsTools.getPeriodDigest(year, month));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Gets movement statistics for any date range: total distance, time moving, daily average distance, average speed, unique places, most active day, top places and frequent routes.")
    AIStatisticsDTO getStatistics(
            @ToolArg(description = "Start date in YYYY-MM-DD format") String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format, inclusive") String endDate) {
        return McpToolCalls.execute(() -> insightsTools.getStatistics(startDate, endDate));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Gets lifetime insights from all of the user's data: countries and cities, time patterns, distance traveled, weather and achievements.")
    JourneyInsights getJourneyInsights() {
        return McpToolCalls.execute(insightsTools::getJourneyInsights);
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Gets how much area the user has explored: number of grid cells visited and covered area in km². Only works when coverage is enabled for the user.")
    CoverageSummary getCoverageSummary(
            @ToolArg(description = "Grid cell size in meters: 20, 50, 250, 1000, 5000, 20000 or 40000. Defaults to 50.", required = false) Integer gridMeters) {
        return McpToolCalls.execute(() -> insightsTools.getCoverageSummary(gridMeters));
    }
}
