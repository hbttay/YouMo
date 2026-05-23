package com.youmo.core.service.impl;

import com.youmo.common.entity.Book;
import com.youmo.common.entity.ChapterContent;
import com.youmo.common.entity.ChapterStructure;
import com.youmo.common.enums.BookStatus;
import com.youmo.common.enums.ChapterContentStatus;
import com.youmo.core.repository.BookRepository;
import com.youmo.core.repository.ChapterContentRepository;
import com.youmo.core.repository.ChapterStructureRepository;
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
    private final ChapterStructureRepository chapterStructureRepository;
    private final BookRepository bookRepository;

    @Override
    @Transactional
    public ChapterContent save(ChapterContent content) {
        Long structureId = content.getStructure().getId();
        // Auto-increment version number
        Integer maxVersion = chapterContentRepository
                .findTopByStructureIdOrderByVersionNumberDesc(structureId)
                .map(ChapterContent::getVersionNumber)
                .orElse(0);
        content.setVersionNumber(maxVersion + 1);

        ChapterContent saved = chapterContentRepository.save(content);

        // When chapter is published, auto-transition book from DRAFT to SERIALIZING
        if (content.getStatus() == ChapterContentStatus.PUBLISHED) {
            ChapterStructure structure = chapterStructureRepository.findById(structureId).orElse(null);
            if (structure != null) {
                Book book = structure.getBook();
                if (book.getStatus() == BookStatus.DRAFT) {
                    book.setStatus(BookStatus.SERIALIZING);
                    bookRepository.save(book);
                }
            }
        }

        return saved;
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
