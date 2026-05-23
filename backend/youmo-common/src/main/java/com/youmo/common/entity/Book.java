package com.youmo.common.entity;

import com.youmo.common.base.BaseEntity;
import com.youmo.common.enums.BookStatus;
import com.youmo.common.enums.CharacterMode;
import com.youmo.common.enums.CreationMode;
import com.youmo.common.enums.LengthType;
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
@Table(name = "book")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Book extends BaseEntity {

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "theme", columnDefinition = "text")
    private String theme;

    @Column(name = "core_idea", columnDefinition = "text")
    private String coreIdea;

    @Column(name = "tone_labels", columnDefinition = "jsonb")
    private String toneLabels;

    @Column(name = "one_sentence", columnDefinition = "text")
    private String oneSentence;

    @Column(name = "target_reader_profile", length = 500)
    private String targetReaderProfile;

    @Column(name = "violence_level", columnDefinition = "smallint default 3 check (violence_level >= 1 and violence_level <= 10)")
    private Short violenceLevel = 3;

    @Column(name = "romance_level", columnDefinition = "smallint default 1 check (romance_level >= 1 and romance_level <= 10)")
    private Short romanceLevel = 1;

    @Column(name = "politics_level", columnDefinition = "smallint default 1 check (politics_level >= 1 and politics_level <= 10)")
    private Short politicsLevel = 1;

    @Column(name = "civility_level", columnDefinition = "smallint default 5 check (civility_level >= 1 and civility_level <= 10)")
    private Short civilityLevel = 5;

    @Enumerated(EnumType.STRING)
    @Column(name = "creation_mode", nullable = false, length = 20)
    private CreationMode creationMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "character_mode", nullable = false, length = 20)
    private CharacterMode characterMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "length_type", nullable = false, length = 10)
    private LengthType lengthType;

    @Column(name = "estimated_words")
    private Integer estimatedWords;

    @Column(name = "is_public")
    private Boolean isPublic = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private BookStatus status;

    @Column(name = "extra_attributes", columnDefinition = "jsonb")
    private String extraAttributes;

    @Column(name = "negative_constraints", columnDefinition = "text")
    private String negativeConstraints;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
}
