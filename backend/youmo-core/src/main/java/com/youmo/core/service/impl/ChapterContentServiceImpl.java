package com.youmo.core.service.impl;

import com.youmo.common.entity.ChapterContent;
import com.youmo.core.repository.ChapterContentRepository;
import com.youmo.core.service.ChapterContentService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChapterContentServiceImpl implements ChapterContentService {

    private final ChapterContentRepository chapterContentRepository;

    @Override
    @Transactional
    public ChapterContent save(ChapterContent content) {
        return chapterContentRepository.save(content);
    }

    @Override
    public Optional<ChapterContent> getLatest(Long structureId) {
        return chapterContentRepository.findTopByStructureIdOrderByVersionNumberDesc(structureId);
    }

    @Override
    public List<ChapterContent> getVersionHistory(Long structureId) {
        return chapterContentRepository.findByStructureIdOrderByVersionNumberDesc(structureId);
    }
}
