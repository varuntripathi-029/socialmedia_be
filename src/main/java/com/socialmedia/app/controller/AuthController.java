package com.socialmedia.app.controller;

import com.socialmedia.app.dto.request.LoginRequest;
import com.socialmedia.app.dto.request.RegisterRequest;
import com.socialmedia.app.dto.request.GoogleAuthRequest;
import com.socialmedia.app.dto.response.AuthResponse;
import com.socialmedia.app.dto.response.UserResponse;
import com.socialmedia.app.model.User;
import com.socialmedia.app.repository.UserRepository;
import com.socialmedia.app.service.AuthService;
import com.socialmedia.app.service.GoogleAuthService;
import com.socialmedia.app.service.JwtService;
import com.socialmedia.app.service.CustomUserDetailsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    // -----------------------------
    // Normal Email/Password Register
    // -----------------------------
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    // -----------------------------
    // Normal Email/Password Login
    // -----------------------------
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // -----------------------------
    // Google OAuth Login
    // -----------------------------
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleAuth(@RequestBody GoogleAuthRequest request) {

        if (request.getIdToken() == null || request.getIdToken().isBlank()) {
            return ResponseEntity.badRequest().build(); // prevents unwanted inserts
        }

        var payload = googleAuthService.verifyToken(request.getIdToken());

        if (payload == null) {
            return ResponseEntity.status(401).build(); // invalid Google token
        }

        String email = payload.getEmail();
        String name = (String) payload.get("name");

        if (email == null) {
            return ResponseEntity.status(400).body(null);
        }

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .username(email.split("@")[0])
                        .fullName(name)
                        .password("GOOGLE_AUTH")
                        .build()));

        var userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtService.generateToken(userDetails);

        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();

        return ResponseEntity.ok(new AuthResponse(token, userResponse));
    }

}
