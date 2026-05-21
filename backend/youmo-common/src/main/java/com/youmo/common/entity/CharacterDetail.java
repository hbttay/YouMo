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
@Table(name = "character_detail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CharacterDetail extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @Column(name = "skill_tree", columnDefinition = "jsonb")
    private String skillTree;

    @Column(name = "talents", columnDefinition = "jsonb")
    private String talents;

    @Column(name = "weaknesses", columnDefinition = "jsonb")
    private String weaknesses;

    @Column(name = "growth_curve", columnDefinition = "jsonb")
    private String growthCurve;

    @Column(name = "core_desire", length = 500)
    private String coreDesire;

    @Column(name = "surface_goal", length = 500)
    private String surfaceGoal;

    @Column(name = "deep_fear", length = 500)
    private String deepFear;

    @Column(name = "bottom_line", length = 500)
    private String bottomLine;

    @Column(name = "value_ranking", columnDefinition = "jsonb")
    private String valueRanking;

    @Column(name = "talkativeness", length = 20)
    private String talkativeness;

    @Column(name = "sentence_style", length = 20)
    private String sentenceStyle;

    @Column(name = "word_preference", length = 20)
    private String wordPreference;

    @Column(name = "emotion_expression", length = 20)
    private String emotionExpression;

    @Column(name = "action_style", length = 20)
    private String actionStyle;

    @Column(name = "effective_chapter", nullable = false)
    private Integer effectiveChapter;

    @Column(name = "expire_chapter")
    private Integer expireChapter;

    @Column(name = "version_number")
    private Integer versionNumber = 1;

    @Column(name = "extra_attributes", columnDefinition = "jsonb")
    private String extraAttributes;
}
