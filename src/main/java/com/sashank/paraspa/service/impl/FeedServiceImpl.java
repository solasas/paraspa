package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.PostResponse;
import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.repository.FeedQuery;
import com.sashank.paraspa.repository.PostRepository;
import com.sashank.paraspa.repository.TrendingPostQuery;
import com.sashank.paraspa.service.FeedService;
import com.sashank.paraspa.service.PostAssembler;
import com.sashank.paraspa.util.CursorUtil;
import com.sashank.paraspa.util.PaginationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FeedServiceImpl implements FeedService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private FeedQuery feedQuery;

    @Autowired
    private TrendingPostQuery trendingPostQuery;

    @Autowired
    private PostAssembler postAssembler;

    /** Replaceable in tests. */
    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<PostResponse> getFeed(Long userId, String cursor, int size) {
        PaginationUtil.requireValidSize(size);
        CursorUtil.Keyset after = CursorUtil.decodeKeyset(cursor);

        // one extra row tells us whether another page exists
        List<FeedQuery.Row> fetched = feedQuery.findFeed(userId,
                after == null ? null : after.createdAt(), after == null ? null : after.id(), size + 1);

        CursorPageResponse<FeedQuery.Row> page = CursorPageResponse.ofCursor(fetched, size,
                row -> CursorUtil.encode(row.createdAt(), row.postId()));

        List<Post> posts = loadInOrder(page.getItems().stream().map(FeedQuery.Row::postId).toList());
        return new CursorPageResponse<>(postAssembler.assemble(posts, userId), page.getNextCursor());
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<PostResponse> getExplore(Long userId, Long skillId, String cursor, int size) {
        PaginationUtil.requireValidSize(size);
        CursorUtil.ScoreKeyset after = CursorUtil.decodeScore(cursor);

        long nowEpoch = Instant.now(clock).getEpochSecond();
        if (after != null && after.asOfEpochSeconds() > nowEpoch + 60) {
            throw new BadRequestException("Invalid cursor");   // a cursor from the future cannot be ours
        }
        // first page: rank as of now (whole seconds); later pages: rank as of the same instant
        long asOfEpoch = after == null ? nowEpoch : after.asOfEpochSeconds();
        LocalDateTime asOf = LocalDateTime.ofEpochSecond(asOfEpoch, 0, ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);

        List<TrendingPostQuery.Scored> ranked = trendingPostQuery.findTrending(
                userId, skillId, asOf,
                after == null ? null : after.score(), after == null ? null : after.id(), size + 1);

        CursorPageResponse<TrendingPostQuery.Scored> page = CursorPageResponse.ofCursor(ranked, size,
                row -> CursorUtil.encodeScore(asOfEpoch, row.score(), row.postId()));

        // hydrate the page's posts (with authors) in one statement, then restore the ranking order
        List<Long> ids = page.getItems().stream().map(TrendingPostQuery.Scored::postId).toList();
        List<Post> posts = loadInOrder(ids);

        return new CursorPageResponse<>(postAssembler.assemble(posts, userId), page.getNextCursor());
    }

    /** Loads the posts (with authors) in one statement and puts them back in the order of {@code ids}. */
    private List<Post> loadInOrder(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Post> byId = new HashMap<>();
        postRepository.findAllWithAuthorByIdIn(ids).forEach(p -> byId.put(p.getId(), p));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}
