package com.youmo.common.entity;

import com.youmo.common.base.BaseEntity;
import com.youmo.common.enums.AnnotationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chapter_content_annotation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChapterContentAnnotation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id", nullable = false)
    private ChapterStructure structure;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_version_id", nullable = false)
    private ChapterContent contentVersion;

    @Column(name = "annotation_type", length = 20, nullable = false)
    private String annotationType = "MANUAL";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AnnotationStatus status = AnnotationStatus.OPEN;

    @Column(name = "char_offset_start", nullable = false)
    private Integer charOffsetStart;

    @Column(name = "char_offset_end", nullable = false)
    private Integer charOffsetEnd;

    @Column(name = "anchor_text", nullable = false, columnDefinition = "text")
    private String anchorText;

    @Column(name = "context_before", columnDefinition = "text")
    private String contextBefore;

    @Column(name = "context_after", columnDefinition = "text")
    private String contextAfter;

    @Column(name = "comment", nullable = false, columnDefinition = "text")
    private String comment;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "severity", length = 10)
    private String severity = "INFO";

    @Column(name = "resolved_comment", columnDefinition = "text")
    private String resolvedComment;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "batch_id", length = 36)
    private String batchId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
}
