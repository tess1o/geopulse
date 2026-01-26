package org.github.tess1o.geopulse.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {
    private String role;
    private String content;
    private String name;
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;
    @JsonProperty("tool_call_id")
    private String toolCallId;

    public ChatMessage() {
    }

    public ChatMessage(String role, String content, String name, List<ToolCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.name = name;
        this.toolCalls = toolCalls;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getName() {
        return name;
    }

    @JsonProperty("tool_calls")
    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    @JsonProperty("tool_call_id")
    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }
}
