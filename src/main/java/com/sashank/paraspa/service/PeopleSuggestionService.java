package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.response.MatchResponse;
import java.util.List;

public interface PeopleSuggestionService {

    /**
     * People worth following: the users whose skills complement mine (the same matching as GET /api/matches,
     * mutual matches first), without anyone I already follow. One page of the result.
     */
    List<MatchResponse> getSuggestedPeople(Long userId, int page, int size);
}
