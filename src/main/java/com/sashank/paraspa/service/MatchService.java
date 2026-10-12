package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.response.MatchResponse;
import java.util.List;

public interface MatchService {
    List<MatchResponse> findMatches(Long userId);
    List<MatchResponse> findMutualMatches(Long userId);
    MatchResponse checkMatch(Long userId, Long otherUserId);
}

