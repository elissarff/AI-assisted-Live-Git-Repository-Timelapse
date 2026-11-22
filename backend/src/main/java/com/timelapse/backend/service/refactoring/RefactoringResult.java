package com.timelapse.backend.service.refactoring;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

/** Only fully completed detector output crosses the worker process boundary. */
public record RefactoringResult(String parentSha, List<Detection> detections) {
    public record Detection(String type, String description, JsonNode beforeLocations, JsonNode afterLocations) {}
}
