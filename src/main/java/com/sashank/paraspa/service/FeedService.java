package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.PostResponse;

public interface FeedService {

    /** Posts by the people I follow plus my own, newest first, cursor-paginated on (createdAt, id). */
    CursorPageResponse<PostResponse> getFeed(Long userId, String cursor, int size);

    /**
     * Trending posts of the last 7 days from people I do not follow (and not mine), ranked by
     * {@code (likeCount + 2 * commentCount) / (ageHours + 2) ^ 1.5}. Optional skill tag filter.
     * The cursor pins the ranking instant, so all pages of one scroll are ranked consistently.
     */
    CursorPageResponse<PostResponse> getExplore(Long userId, Long skillId, String cursor, int size);
}
