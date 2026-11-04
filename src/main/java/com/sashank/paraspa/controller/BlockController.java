package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.service.BlockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class BlockController {

    @Autowired
    private BlockService blockService;

    /** Idempotent. */
    @PutMapping("/users/{userId}/block")
    public ResponseEntity<Void> block(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long userId) {
        blockService.block(currentUserId, userId);
        return ResponseEntity.noContent().build();
    }

    /** Idempotent. */
    @DeleteMapping("/users/{userId}/block")
    public ResponseEntity<Void> unblock(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long userId) {
        blockService.unblock(currentUserId, userId);
        return ResponseEntity.noContent().build();
    }

    /** The people I have blocked. */
    @GetMapping("/blocks")
    public ResponseEntity<List<UserResponse>> getBlocks(
            @RequestAttribute("userId") Long currentUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(blockService.getBlockedUsers(currentUserId, page, size));
    }
}
