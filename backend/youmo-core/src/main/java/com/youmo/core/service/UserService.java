package com.youmo.core.service;

import com.youmo.common.entity.User;
import java.util.Optional;

public interface UserService {

    User create(String email, String username, String rawPassword);

    Optional<User> getById(Long id);

    Optional<User> getByEmail(String email);

    boolean existsByEmail(String email);

    User login(String account, String rawPassword);

    void changePassword(Long userId, String oldPassword, String newPassword);

    User updateProfile(Long userId, String email);
}
