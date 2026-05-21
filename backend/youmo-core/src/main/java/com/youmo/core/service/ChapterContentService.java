package com.youmo.core.service;

import com.youmo.common.entity.ChapterContent;
import java.util.List;
import java.util.Optional;

public interface ChapterContentService {

    ChapterContent save(ChapterContent content);

    Optional<ChapterContent> getLatest(Long structureId);

    List<ChapterContent> getVersionHistory(Long structureId);
}
