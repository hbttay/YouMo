package com.youmo.core.repository;

import com.youmo.common.entity.Feedback;
import com.youmo.common.enums.FeedbackCategory;
import com.youmo.common.enums.FeedbackSeverity;
import com.youmo.common.enums.FeedbackStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByStatusOrderByCreatedAtDesc(FeedbackStatus status);

    List<Feedback> findByCategoryOrderByCreatedAtDesc(FeedbackCategory category);

    List<Feedback> findBySeverityOrderByCreatedAtDesc(FeedbackSeverity severity);

    List<Feedback> findByEscalateToTechTrueOrderByCreatedAtDesc();

    List<Feedback> findAllByOrderByCreatedAtDesc();
}
