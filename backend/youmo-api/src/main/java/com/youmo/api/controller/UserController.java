package com.youmo.api.controller;

import com.youmo.api.dto.request.CreateUserRequest;
import com.youmo.api.dto.response.CharacterResponse;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.entity.User;
import com.youmo.core.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ApiResponse<Long> register(@RequestBody CreateUserRequest req) {
        User user = userService.create(req.getEmail(), req.getPassword());
        return ApiResponse.ok(user.getId());
    }

    @GetMapping("/{id}")
    public ApiResponse<User> getById(@PathVariable Long id) {
        return userService.getById(id)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail(404, "用户不存在"));
    }
}
