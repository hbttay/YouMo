package com.youmo.core.service;

import com.youmo.common.entity.ChapterSummary;
import java.util.Optional;

public interface ChapterSummaryService {

    ChapterSummary save(ChapterSummary summary);

    Optional<ChapterSummary> getByStructureId(Long structureId);
}
