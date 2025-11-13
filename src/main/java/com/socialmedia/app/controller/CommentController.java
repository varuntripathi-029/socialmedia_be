package com.socialmedia.app.controller;

import com.socialmedia.app.dto.request.CreateCommentRequest;
import com.socialmedia.app.dto.response.ApiResponse;
import com.socialmedia.app.dto.response.CommentResponse;
import com.socialmedia.app.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        return ResponseEntity.ok(commentService.createComment(postId, request));
    }

    @GetMapping
    public ResponseEntity<List<CommentResponse>> getPostComments(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getPostComments(postId));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponse> deleteComment(@PathVariable Long commentId) {
        commentService.deleteComment(commentId);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Comment deleted successfully")
                .build());
    }

    @GetMapping("/count")
    public ResponseEntity<Long> getCommentsCount(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getCommentsCount(postId));
    }
}