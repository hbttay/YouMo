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
@Table(name = "chapter_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChapterSummary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id", nullable = false)
    private ChapterStructure structure;

    @Column(name = "summary_version", nullable = false)
    private Integer summaryVersion;

    @Column(name = "core_events", columnDefinition = "jsonb")
    private String coreEvents;

    @Column(name = "appearing_characters", columnDefinition = "jsonb")
    private String appearingCharacters;

    @Column(name = "character_state_changes", columnDefinition = "jsonb")
    private String characterStateChanges;

    @Column(name = "new_foreshadowings", columnDefinition = "jsonb")
    private String newForeshadowings;

    @Column(name = "recycled_foreshadowings", columnDefinition = "jsonb")
    private String recycledForeshadowings;

    @Column(name = "emotion_curve_point", columnDefinition = "jsonb")
    private String emotionCurvePoint;

    @Column(name = "key_scenes", columnDefinition = "jsonb")
    private String keyScenes;

    @Column(name = "world_elements", columnDefinition = "jsonb")
    private String worldElements;

    @Column(name = "summary_type", length = 10)
    private String summaryType = "SHORT";

    @Column(name = "is_permanent")
    private Boolean isPermanent = false;

    @Column(name = "narrative_summary", columnDefinition = "TEXT")
    private String narrativeSummary;

    @Column(name = "extra_attributes", columnDefinition = "jsonb")
    private String extraAttributes;
}
