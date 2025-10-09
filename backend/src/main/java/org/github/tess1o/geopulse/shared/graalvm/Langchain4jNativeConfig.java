package org.github.tess1o.geopulse.shared.graalvm;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(
        targets = {
                dev.langchain4j.agent.tool.ToolExecutionRequest.class,
                dev.langchain4j.agent.tool.ToolMemoryId.class,
                dev.langchain4j.exception.ToolArgumentsException.class,
                dev.langchain4j.exception.ToolExecutionException.class,
                dev.langchain4j.internal.Exceptions.class,
                dev.langchain4j.internal.Json.class,
                dev.langchain4j.internal.Utils.class,
                dev.langchain4j.internal.ValidationUtils.class,
                dev.langchain4j.invocation.InvocationContext.class,
                dev.langchain4j.invocation.InvocationParameters.class,
                dev.langchain4j.service.tool.DefaultToolExecutor.class,
                dev.langchain4j.internal.Json.class,
                dev.langchain4j.internal.RetryUtils.class,
                dev.langchain4j.internal.RetryUtils.RetryPolicy.class,
                dev.langchain4j.service.AiServices.class
        }
)
public class Langchain4jNativeConfig {
}
