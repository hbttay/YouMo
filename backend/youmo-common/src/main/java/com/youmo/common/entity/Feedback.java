package com.youmo.common.entity;

import com.youmo.common.base.BaseEntity;
import com.youmo.common.enums.FeedbackCategory;
import com.youmo.common.enums.FeedbackSeverity;
import com.youmo.common.enums.FeedbackStatus;
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
@Table(name = "feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Feedback extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "contact", length = 255)
    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private FeedbackCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private FeedbackSeverity severity;

    @Column(name = "escalate_to_tech")
    private Boolean escalateToTech = false;

    @Column(name = "ai_analysis", columnDefinition = "text")
    private String aiAnalysis;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private FeedbackStatus status = FeedbackStatus.PENDING;

    @Column(name = "extra_attributes", columnDefinition = "jsonb")
    private String extraAttributes;
}
