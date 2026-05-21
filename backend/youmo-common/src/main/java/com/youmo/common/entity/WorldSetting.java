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
@Table(name = "world_setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorldSetting extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false, unique = true)
    private Book book;

    @Column(name = "era", columnDefinition = "text")
    private String era;

    @Column(name = "geography", columnDefinition = "text")
    private String geography;

    @Column(name = "history_events", columnDefinition = "jsonb")
    private String historyEvents;

    @Column(name = "politics", columnDefinition = "text")
    private String politics;

    @Column(name = "economy", columnDefinition = "text")
    private String economy;

    @Column(name = "culture", columnDefinition = "text")
    private String culture;

    @Column(name = "military", columnDefinition = "text")
    private String military;

    @Column(name = "core_rule_type", length = 50)
    private String coreRuleType;

    @Column(name = "core_rule_summary", columnDefinition = "text")
    private String coreRuleSummary;

    @Column(name = "extra_attributes", columnDefinition = "jsonb")
    private String extraAttributes;
}
