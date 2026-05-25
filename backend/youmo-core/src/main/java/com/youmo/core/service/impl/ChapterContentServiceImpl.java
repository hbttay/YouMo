package com.youmo.core.service.impl;

import com.youmo.common.entity.Book;
import com.youmo.common.entity.ChapterContent;
import com.youmo.common.entity.ChapterContentArchive;
import com.youmo.common.entity.ChapterStructure;
import com.youmo.common.enums.BookStatus;
import com.youmo.common.enums.ChapterContentStatus;
import com.youmo.core.repository.BookRepository;
import com.youmo.core.repository.ChapterContentArchiveRepository;
import com.youmo.core.repository.ChapterContentRepository;
import com.youmo.core.repository.ChapterStructureRepository;
import com.youmo.core.service.ChapterContentService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChapterContentServiceImpl implements ChapterContentService {

    private final ChapterContentRepository chapterContentRepository;
    private final ChapterContentArchiveRepository archiveRepository;
    private final ChapterStructureRepository chapterStructureRepository;
    private final BookRepository bookRepository;

    @Override
    @Transactional
    public ChapterContent save(ChapterContent content) {
        Long structureId = content.getStructure().getId();

        // Archive the previous latest version (keep main table lean)
        chapterContentRepository
                .findTopByStructureIdOrderByVersionNumberDesc(structureId)
                .ifPresent(prev -> {
                    ChapterContentArchive archive = new ChapterContentArchive();
                    archive.setOriginalContentId(prev.getId());
                    archive.setStructureId(structureId);
                    archive.setVersionNumber(prev.getVersionNumber());
                    archive.setContent(prev.getContent());
                    archive.setWordCount(prev.getWordCount());
                    archive.setSource(prev.getSource());
                    archive.setStatus(prev.getStatus());
                    archive.setArchivedAt(Instant.now());
                    archiveRepository.save(archive);
                    chapterContentRepository.delete(prev);
                });

        // Determine next version number (check both tables)
        int maxArchiveVer = archiveRepository
                .findByStructureIdOrderByVersionNumberDesc(structureId)
                .stream()
                .findFirst()
                .map(ChapterContentArchive::getVersionNumber)
                .orElse(0);
        content.setVersionNumber(maxArchiveVer + 1);

        ChapterContent saved = chapterContentRepository.save(content);

        // Auto-transition book from DRAFT to SERIALIZING on first publish
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
        // Merge current + archive, return sorted by version desc
        List<ChapterContent> current = chapterContentRepository
                .findByStructureIdOrderByVersionNumberDesc(structureId);

        List<ChapterContentArchive> archived = archiveRepository
                .findByStructureIdOrderByVersionNumberDesc(structureId);

        // Convert archives to lightweight content DTOs for the frontend
        List<ChapterContent> result = new ArrayList<>(current);
        for (ChapterContentArchive a : archived) {
            ChapterContent c = new ChapterContent();
            c.setId(a.getOriginalContentId() != null ? a.getOriginalContentId() : a.getId());
            c.setVersionNumber(a.getVersionNumber());
            c.setContent(a.getContent());
            c.setWordCount(a.getWordCount());
            c.setSource(a.getSource());
            c.setStatus(a.getStatus());
            // Use archive's created_at so frontend shows original time
            result.add(c);
        }

        result.sort(Comparator.comparingInt(ChapterContent::getVersionNumber).reversed());
        return result;
    }

    @Override
    @Transactional
    public void updateStreamBuffer(Long structureId, String buffer) {
        chapterContentRepository
            .findTopByStructureIdOrderByVersionNumberDesc(structureId)
            .ifPresent(c -> {
                c.setStreamBuffer(buffer);
                chapterContentRepository.save(c);
            });
    }

    @Override
    public String getStreamBuffer(Long structureId) {
        return chapterContentRepository
            .findTopByStructureIdOrderByVersionNumberDesc(structureId)
            .map(ChapterContent::getStreamBuffer)
            .orElse(null);
    }

    @Override
    @Transactional
    public void clearStreamBuffer(Long structureId) {
        chapterContentRepository
            .findTopByStructureIdOrderByVersionNumberDesc(structureId)
            .ifPresent(c -> {
                c.setStreamBuffer(null);
                chapterContentRepository.save(c);
            });
    }
}
