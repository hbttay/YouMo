package com.youmo.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.youmo.common.base.BaseEntity;
import com.youmo.common.enums.NodeStatus;
import com.youmo.common.enums.NodeType;
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
@Table(name = "chapter_structure")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChapterStructure extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ChapterStructure parent;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 20)
    private NodeType nodeType;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "writing_goal", columnDefinition = "text")
    private String writingGoal;

    @Column(name = "key_events", columnDefinition = "jsonb")
    private String keyEvents;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private NodeStatus status = NodeStatus.DRAFT;

    @Column(name = "is_important_plot")
    private Boolean isImportantPlot = false;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "extra_attributes", columnDefinition = "jsonb")
    private String extraAttributes;

    @JsonProperty("parent_id")
    public Long getParentId() {
        return parent != null ? parent.getId() : null;
    }
}
