package com.youmo.api.dto.response;

import com.youmo.common.entity.User;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class UserResponse {
    private Long id;
    private String email;
    private String username;
    private String status;
    private LocalDateTime createdAt;

    public static UserResponse from(User user) {
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setEmail(user.getEmail());
        r.setUsername(user.getUsername());
        r.setStatus(user.getStatus().name());
        r.setCreatedAt(user.getCreatedAt());
        return r;
    }
}
