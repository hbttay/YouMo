package com.youmo.api.controller;

import com.youmo.api.security.SecurityUtil;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.Book;
import com.youmo.common.entity.WorldSetting;
import com.youmo.core.service.BookService;
import com.youmo.core.service.WorldSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/books/{bookId}/world-setting")
@RequiredArgsConstructor
public class WorldSettingController {

    private final WorldSettingService worldSettingService;
    private final BookService bookService;

    private void assertOwnership(Long bookId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Book book = bookService.getById(bookId)
            .orElseThrow(() -> new BusinessException(404, "书籍不存在"));
        if (book.getOwner() == null || !book.getOwner().getId().equals(userId)) {
            throw new BusinessException(403, "无权访问此书");
        }
    }

    @GetMapping
    public ApiResponse<WorldSetting> get(@PathVariable Long bookId) {
        assertOwnership(bookId);
        return worldSettingService.getByBookId(bookId)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail(404, "世界观设定不存在"));
    }

    @PutMapping
    public ApiResponse<WorldSetting> saveOrUpdate(@PathVariable Long bookId, @RequestBody WorldSetting setting) {
        assertOwnership(bookId);
        WorldSetting saved = worldSettingService.saveOrUpdate(bookId, setting);
        log.info("World setting saved: bookId={}", bookId);
        return ApiResponse.ok(saved);
    }
}
