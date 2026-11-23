package com.timelapse.backend.service.ai.analysis;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Provider-neutral contract; results are validated before persistence.
 */
public interface LlmProvider {

    JsonNode explain(String evidence) throws Exception;

    default JsonNode analyze(String instructions, String evidence) throws Exception {
        return explain(instructions + "\n" + evidence);
    }

    String provider();

    String model();
}
