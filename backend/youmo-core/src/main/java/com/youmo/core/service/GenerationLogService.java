package com.youmo.core.service;

import com.youmo.common.entity.GenerationLog;
import java.util.List;

public interface GenerationLogService {

    GenerationLog log(GenerationLog log);

    List<GenerationLog> listByStructureId(Long structureId);

    void logNonStreaming(String responseBody, String model, long durationMs,
                         Long structureId, String promptSnapshot, boolean success);
}
