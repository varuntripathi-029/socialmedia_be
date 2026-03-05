package com.socialmedia.app.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class WelcomeController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> welcome() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> response = new HashMap<>();

        if (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
            response.put("message", "Welcome back!");
            response.put("authenticated", true);
            response.put("user", authentication.getName());
            response.put("action", "Redirect to Home Page");
        } else {
            response.put("message", "Welcome to the Social Media Platform!");
            response.put("authenticated", false);
            response.put("action", "Please Log In or Sign Up");
        }

        return ResponseEntity.ok(response);
    }
}
