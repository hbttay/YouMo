package com.youmo.core.service.impl;

import com.youmo.common.entity.ChapterSummary;
import com.youmo.core.repository.ChapterSummaryRepository;
import com.youmo.core.service.ChapterSummaryService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChapterSummaryServiceImpl implements ChapterSummaryService {

    private final ChapterSummaryRepository chapterSummaryRepository;

    @Override
    @Transactional
    public ChapterSummary save(ChapterSummary summary) {
        return chapterSummaryRepository.save(summary);
    }

    @Override
    public Optional<ChapterSummary> getByStructureId(Long structureId) {
        return chapterSummaryRepository.findByStructureId(structureId);
    }
}
