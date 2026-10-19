package org.github.tess1o.geopulse.mcp;

import io.quarkiverse.mcp.server.ToolCallException;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.service.AIToolException;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import java.util.function.Supplier;

/**
 * Runs an MCP tool and turns expected failures into tool errors the client shows to the model as
 * {@code CODE: message}.
 */
@Slf4j
final class McpToolCalls {

    private McpToolCalls() {
    }

    static <T> T execute(Supplier<T> action) {
        try {
            return action.get();
        } catch (AIToolException e) {
            log.error("Error while executing tool", e);
            throw new ToolCallException(e.getCode() + ": " + e.getMessage(), e);
        } catch (GeoPulseException e) {
            log.error("Error while executing tool", e);
            throw new ToolCallException(e.code().name() + ": " + e.detail(), e);
        }
    }
}
