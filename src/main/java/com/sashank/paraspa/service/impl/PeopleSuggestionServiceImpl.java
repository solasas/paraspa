package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.MatchResponse;
import com.sashank.paraspa.repository.FollowRepository;
import com.sashank.paraspa.service.MatchService;
import com.sashank.paraspa.service.PeopleSuggestionService;
import com.sashank.paraspa.util.PaginationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PeopleSuggestionServiceImpl implements PeopleSuggestionService {

    @Autowired
    private MatchService matchService;

    @Autowired
    private FollowRepository followRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MatchResponse> getSuggestedPeople(Long userId, int page, int size) {
        PaginationUtil.toPageable(page, size);   // validates page/size before doing any work

        Set<Long> alreadyFollowed = new HashSet<>(followRepository.findFollowingIdsByFollowerId(userId));

        // MatchService already leaves out the user and puts mutual matches first; keep its order
        List<MatchResponse> suggestions = matchService.findMatches(userId).stream()
                .filter(match -> !alreadyFollowed.contains(match.getUser().getId()))
                .toList();

        return PaginationUtil.slice(suggestions, page, size);
    }
}
