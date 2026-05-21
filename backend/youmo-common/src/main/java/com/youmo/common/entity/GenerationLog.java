package com.youmo.common.entity;

import com.youmo.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "generation_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GenerationLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id")
    private ChapterStructure structure;

    @Column(name = "prompt_snapshot", columnDefinition = "text")
    private String promptSnapshot;

    @Column(name = "model", length = 50)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "cost", precision = 8, scale = 4)
    private BigDecimal cost;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "success")
    private Boolean success = true;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
}
