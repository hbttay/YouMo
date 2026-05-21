package com.youmo.common.entity;

import com.youmo.common.base.BaseEntity;
import com.youmo.common.enums.DepthLevel;
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
@Table(name = "character")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Character extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "gender", length = 50)
    private String gender;

    @Column(name = "age_description", length = 100)
    private String ageDescription;

    @Column(name = "appearance", columnDefinition = "text")
    private String appearance;

    @Column(name = "origin", length = 200)
    private String origin;

    @Column(name = "identity", length = 200)
    private String identity;

    @Column(name = "category_template_id")
    private Long categoryTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "depth_level", nullable = false, length = 2, columnDefinition = "char(2) default 'L1'")
    private DepthLevel depthLevel = DepthLevel.L1;

    @Column(name = "is_auto_created")
    private Boolean isAutoCreated = false;

    @Column(name = "is_archived")
    private Boolean isArchived = false;

    @Column(name = "appear_chapters", columnDefinition = "jsonb")
    private String appearChapters;

    @Column(name = "extra_attributes", columnDefinition = "jsonb")
    private String extraAttributes;
}
