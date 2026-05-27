package com.youmo.common.entity;

import com.youmo.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "character_relationship")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CharacterRelationship extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_character_id", nullable = false)
    private Character sourceCharacter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_character_id", nullable = false)
    private Character targetCharacter;

    @Column(name = "relationship_type", nullable = false, length = 50)
    private String relationshipType;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "intimacy_level")
    private Short intimacyLevel = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "start_chapter_id")
    private ChapterStructure startChapter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "end_chapter_id")
    private ChapterStructure endChapter;
}
