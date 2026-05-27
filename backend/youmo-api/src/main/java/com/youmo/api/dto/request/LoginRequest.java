package com.youmo.api.dto.request;

import lombok.Data;

@Data
public class LoginRequest {
    private String account; // email or username
    private String password;
}
