package com.socialmedia.app.controller;

import com.socialmedia.app.dto.response.ApiResponse;
import com.socialmedia.app.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts/{postId}/likes")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping
    public ResponseEntity<ApiResponse> likePost(@PathVariable Long postId) {
        likeService.likePost(postId);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Post liked successfully")
                .build());
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse> unlikePost(@PathVariable Long postId) {
        likeService.unlikePost(postId);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Post unliked successfully")
                .build());
    }

    @GetMapping("/count")
    public ResponseEntity<Long> getLikesCount(@PathVariable Long postId) {
        return ResponseEntity.ok(likeService.getLikesCount(postId));
    }
}