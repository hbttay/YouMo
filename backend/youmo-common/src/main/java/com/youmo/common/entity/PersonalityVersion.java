package com.youmo.common.entity;

import com.youmo.common.base.BaseEntity;
import com.youmo.common.enums.VersionSource;
import com.youmo.common.enums.VersionStatus;
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
@Table(name = "personality_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PersonalityVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @Column(name = "tags", nullable = false, columnDefinition = "jsonb")
    private String tags;

    @Column(name = "enneagram", length = 50)
    private String enneagram;

    @Column(name = "archetype", length = 50)
    private String archetype;

    @Column(name = "effective_chapter", nullable = false)
    private Integer effectiveChapter;

    @Column(name = "expire_chapter")
    private Integer expireChapter;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private VersionStatus status = VersionStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20)
    private VersionSource source = VersionSource.MANUAL;

    @Column(name = "extra_attributes", columnDefinition = "jsonb")
    private String extraAttributes;
}
