package com.timelapse.backend.service.git.metrics;

import com.timelapse.backend.dto.FileChangeDto;

final class MetricPaths {

    private MetricPaths() {
    }
    static String path(FileChangeDto c) {
        String p = c.newPath() != null && !"/dev/null".equals(c.newPath()) ? c.newPath() : c.oldPath();
        return p == null || "/dev/null".equals(p) ? null : p;
    }
}
