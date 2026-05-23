package com.youmo.common.entity;

import com.youmo.common.base.BaseEntity;
import com.youmo.common.enums.ChapterContentStatus;
import com.youmo.common.enums.ContentSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chapter_content_archive")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChapterContentArchive extends BaseEntity {

    @Column(name = "original_content_id")
    private Long originalContentId;

    @Column(name = "structure_id", nullable = false)
    private Long structureId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "word_count")
    private Integer wordCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20)
    private ContentSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ChapterContentStatus status = ChapterContentStatus.DRAFT;

    @Column(name = "archived_at")
    private Instant archivedAt = Instant.now();
}
