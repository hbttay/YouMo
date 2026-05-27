package com.youmo.common.entity;

import com.youmo.common.base.BaseEntity;
import com.youmo.common.enums.ForeshadowingStatus;
import com.youmo.common.enums.ForeshadowingType;
import com.youmo.common.enums.ImportanceLevel;
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
@Table(name = "foreshadowing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Foreshadowing extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "description", columnDefinition = "text", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "foreshadowing_type", nullable = false, length = 20)
    private ForeshadowingType foreshadowingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "importance", nullable = false, length = 10)
    private ImportanceLevel importance = ImportanceLevel.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ForeshadowingStatus status = ForeshadowingStatus.ACTIVE;

    @Column(name = "target_entity", length = 200)
    private String targetEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_chapter_id")
    private ChapterStructure createdChapter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planned_recycle_chapter_id")
    private ChapterStructure plannedRecycleChapter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recycled_chapter_id")
    private ChapterStructure recycledChapter;
}
