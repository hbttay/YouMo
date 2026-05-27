package com.youmo.core.service;

import java.util.Map;

public interface TextMetricsService {

    /** Compute all Java-based text metrics from chapter content */
    Map<String, Object> computeMetrics(String text);
}
