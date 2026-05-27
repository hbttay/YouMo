package com.youmo.core.service.impl;

import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.User;
import com.youmo.common.enums.UserStatus;
import com.youmo.core.repository.UserRepository;
import com.youmo.core.security.LoginRateLimiter;
import com.youmo.core.service.UserService;
import com.youmo.core.validation.UsernameValidator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter rateLimiter;

    private static final String BAD_CREDENTIALS = "账号或密码错误";

    @Override
    @Transactional
    public User create(String email, String username, String rawPassword) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(400, "邮箱不能为空");
        }
        if (rawPassword == null || rawPassword.length() < 4) {
            throw new BusinessException(400, "密码至少 4 位");
        }
        if (userRepository.existsByEmail(email.strip())) {
            throw new BusinessException(400, "邮箱已被注册");
        }

        User user = new User();
        user.setEmail(email.strip());

        if (username != null && !username.isBlank()) {
            String err = UsernameValidator.validate(username);
            if (err != null) throw new BusinessException(400, err);
            String normalized = UsernameValidator.normalize(username);
            if (userRepository.existsByUsername(normalized)) {
                throw new BusinessException(400, "用户名已被使用");
            }
            user.setUsername(normalized);
        }

        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    @Override
    public User login(String account, String rawPassword) {
        if (account == null || account.isBlank()) {
            throw new BusinessException(401, BAD_CREDENTIALS);
        }

        String normalized = account.strip();
        if (rateLimiter.isBlocked(normalized)) {
            long secs = rateLimiter.remainingSeconds(normalized);
            long mins = secs / 60;
            throw new BusinessException(429,
                "登录尝试过于频繁，请在 " + mins + " 分钟后重试");
        }

        User user = resolve(normalized);
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            rateLimiter.recordFailure(normalized);
            int remain = rateLimiter.remainingAttempts(normalized);
            String hint = remain > 0 ? "（剩余 " + remain + " 次尝试）" : "（账号已临时锁定）";
            throw new BusinessException(401, BAD_CREDENTIALS + hint);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(403, "账号已被禁用");
        }

        rateLimiter.reset(normalized);
        return user;
    }

    private User resolve(String account) {
        if (account.contains("@")) {
            return userRepository.findByEmail(account).orElse(null);
        }
        return userRepository.findByUsername(account)
            .or(() -> userRepository.findByEmail(account))
            .orElse(null);
    }

    @Override
    public Optional<User> getById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public Optional<User> getByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(400, "原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public User updateProfile(Long userId, String email) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        if (email != null && !email.equals(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw new BusinessException(400, "邮箱已被使用");
            }
            user.setEmail(email);
            userRepository.save(user);
        }
        return user;
    }
}
