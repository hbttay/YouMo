package com.youmo.api.controller;

import com.youmo.common.base.ApiResponse;
import com.youmo.common.entity.WorldSetting;
import com.youmo.core.service.WorldSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{bookId}/world-setting")
@RequiredArgsConstructor
public class WorldSettingController {

    private final WorldSettingService worldSettingService;

    @GetMapping
    public ApiResponse<WorldSetting> get(@PathVariable Long bookId) {
        return worldSettingService.getByBookId(bookId)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail(404, "世界观设定不存在"));
    }

    @PutMapping
    public ApiResponse<WorldSetting> saveOrUpdate(@PathVariable Long bookId, @RequestBody WorldSetting setting) {
        return ApiResponse.ok(worldSettingService.saveOrUpdate(bookId, setting));
    }
}
