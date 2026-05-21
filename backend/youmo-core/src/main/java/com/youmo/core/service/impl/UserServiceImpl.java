package com.youmo.core.service.impl;

import com.youmo.common.entity.User;
import com.youmo.core.repository.UserRepository;
import com.youmo.core.service.UserService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public User create(String email, String rawPassword) {
        // TODO Phase 2: BCrypt 加密替换
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(rawPassword);
        return userRepository.save(user);
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
}
