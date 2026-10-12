package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.response.MatchResponse;
import com.sashank.paraspa.service.MatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    @Autowired
    private MatchService matchService;

    @GetMapping
    public ResponseEntity<List<MatchResponse>> findMatches(
            @RequestAttribute("userId") Long userId) {
        List<MatchResponse> matches = matchService.findMatches(userId);
        return ResponseEntity.ok(matches);
    }

    @GetMapping("/mutual")
    public ResponseEntity<List<MatchResponse>> findMutualMatches(
            @RequestAttribute("userId") Long userId) {
        List<MatchResponse> matches = matchService.findMutualMatches(userId);
        return ResponseEntity.ok(matches);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<MatchResponse> checkMatch(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long userId) {
        MatchResponse match = matchService.checkMatch(currentUserId, userId);
        return ResponseEntity.ok(match);
    }
}

