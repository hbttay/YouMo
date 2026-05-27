package com.youmo.core.service;

import com.youmo.common.entity.Feedback;
import java.util.List;
import java.util.Map;

public interface FeedbackService {

    Feedback create(Feedback feedback);

    Feedback getById(Long id);

    List<Feedback> listAll(String status, String category, String severity, Boolean escalateToTech);

    Feedback updateStatus(Long id, String status);

    Feedback analyze(Long id);

    Map<String, Long> getStats();

    void delete(Long id);
}
