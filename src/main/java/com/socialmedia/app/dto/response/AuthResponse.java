package com.socialmedia.app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private String token;
    private String type;
    private UserResponse user;
    private Boolean needsUsername;

    public AuthResponse(String token, UserResponse user) {
        this.token = token;
        this.type = "Bearer";
        this.user = user;
    }

    public static AuthResponse justNeedsUsername() {
        AuthResponse resp = new AuthResponse();
        resp.setNeedsUsername(true);
        return resp;
    }
}