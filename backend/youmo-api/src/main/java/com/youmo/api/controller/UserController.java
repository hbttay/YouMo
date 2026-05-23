package com.youmo.api.controller;

import com.youmo.api.dto.request.ChangePasswordRequest;
import com.youmo.api.dto.request.CreateUserRequest;
import com.youmo.api.dto.request.LoginRequest;
import com.youmo.api.dto.response.LoginResponse;
import com.youmo.api.dto.response.UserResponse;
import com.youmo.api.security.JwtTokenProvider;
import com.youmo.api.security.SecurityUtil;
import com.youmo.common.base.ApiResponse;
import com.youmo.common.entity.User;
import com.youmo.core.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@RequestBody CreateUserRequest req) {
        User user = userService.create(req.getEmail(), req.getPassword());
        return ApiResponse.ok(UserResponse.from(user));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req) {
        User user = userService.login(req.getEmail(), req.getPassword());
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return ApiResponse.ok(new LoginResponse(token, UserResponse.from(user)));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getById(@PathVariable Long id) {
        return userService.getById(id)
                .map(u -> ApiResponse.ok(UserResponse.from(u)))
                .orElse(ApiResponse.fail(404, "用户不存在"));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() {
        Long userId = SecurityUtil.getCurrentUserId();
        return userService.getById(userId)
                .map(u -> ApiResponse.ok(UserResponse.from(u)))
                .orElse(ApiResponse.fail(404, "用户不存在"));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody ChangePasswordRequest req) {
        Long userId = SecurityUtil.getCurrentUserId();
        userService.changePassword(userId, req.getOldPassword(), req.getNewPassword());
        return ApiResponse.ok();
    }

    @PutMapping("/profile")
    public ApiResponse<UserResponse> updateProfile(@RequestBody LoginRequest req) {
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userService.updateProfile(userId, req.getEmail());
        return ApiResponse.ok(UserResponse.from(user));
    }
}
