package com.youmo.api.dto.request;

import lombok.Data;

@Data
public class CreateUserRequest {
    private String email;
    private String username;
    private String password;
}
