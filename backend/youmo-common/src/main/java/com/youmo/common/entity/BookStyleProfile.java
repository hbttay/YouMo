package com.youmo.common.entity;

import com.youmo.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "book_style_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookStyleProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false, unique = true)
    private Book book;

    // Java-calculated metrics
    @Column(name = "avg_sentence_length")
    private Double avgSentenceLength;

    @Column(name = "dialogue_ratio")
    private Double dialogueRatio;

    @Column(name = "paragraph_style", length = 20)
    private String paragraphStyle;

    @Column(name = "description_action_ratio")
    private Double descriptionActionRatio;

    @Column(name = "vocabulary_richness")
    private Double vocabularyRichness;

    @Column(name = "sentence_variety")
    private Double sentenceVariety;

    // AI-calculated
    @Column(name = "chapter_opening_pattern", columnDefinition = "jsonb")
    private String chapterOpeningPattern;

    @Column(name = "tone_analysis", columnDefinition = "jsonb")
    private String toneAnalysis;

    @Column(name = "writing_habits", columnDefinition = "jsonb")
    private String writingHabits;

    @Column(name = "sample_chapter_count")
    private Integer sampleChapterCount;

    // Overall style label
    @Column(name = "style_label", length = 100)
    private String styleLabel;
}
