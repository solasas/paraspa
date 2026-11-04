package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.MatchResponse;
import com.sashank.paraspa.dto.response.PostResponse;
import com.sashank.paraspa.service.FeedService;
import com.sashank.paraspa.service.PeopleSuggestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FeedController {

    @Autowired
    private FeedService feedService;

    @Autowired
    private PeopleSuggestionService peopleSuggestionService;

    /** Home feed: posts by people I follow plus my own, newest first. */
    @GetMapping("/feed")
    public ResponseEntity<CursorPageResponse<PostResponse>> getFeed(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(feedService.getFeed(userId, cursor, size));
    }

    /** Discovery: trending posts of the last 7 days from people I do not follow. */
    @GetMapping("/explore")
    public ResponseEntity<CursorPageResponse<PostResponse>> getExplore(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Long skillId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(feedService.getExplore(userId, skillId, cursor, size));
    }

    /** Suggested people to follow: skill matches I do not follow yet. */
    @GetMapping("/explore/people")
    public ResponseEntity<List<MatchResponse>> getSuggestedPeople(
            @RequestAttribute("userId") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(peopleSuggestionService.getSuggestedPeople(userId, page, size));
    }
}
