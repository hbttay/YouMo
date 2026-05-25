package com.youmo.common.entity;

import com.youmo.common.base.BaseEntity;
import com.youmo.common.enums.ChapterContentStatus;
import com.youmo.common.enums.ContentSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chapter_content")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChapterContent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id", nullable = false)
    private ChapterStructure structure;

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

    @Column(name = "storage_type", length = 10, columnDefinition = "varchar(10) default 'FULL'")
    private String storageType = "FULL";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_version_id")
    private ChapterContent baseVersion;

    @Column(name = "diff_data", columnDefinition = "text")
    private String diffData;

    @Column(name = "stream_buffer", columnDefinition = "text")
    private String streamBuffer;
}
