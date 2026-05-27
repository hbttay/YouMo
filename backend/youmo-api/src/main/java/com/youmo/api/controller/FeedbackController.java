package com.youmo.api.controller;

import com.youmo.common.base.ApiResponse;
import com.youmo.common.entity.Feedback;
import com.youmo.core.service.FeedbackService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ApiResponse<Feedback> create(@RequestBody Feedback feedback) {
        return ApiResponse.ok(feedbackService.create(feedback));
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean escalate_to_tech) {
        return ApiResponse.ok(feedbackService.listAll(status, category, severity, escalate_to_tech));
    }

    @GetMapping("/{id}")
    public ApiResponse<Feedback> get(@PathVariable Long id) {
        return ApiResponse.ok(feedbackService.getById(id));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Feedback> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(feedbackService.updateStatus(id, body.get("status")));
    }

    @PostMapping("/{id}/analyze")
    public ApiResponse<Feedback> analyze(@PathVariable Long id) {
        return ApiResponse.ok(feedbackService.analyze(id));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats() {
        return ApiResponse.ok(feedbackService.getStats());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        feedbackService.delete(id);
        return ApiResponse.ok();
    }
}
