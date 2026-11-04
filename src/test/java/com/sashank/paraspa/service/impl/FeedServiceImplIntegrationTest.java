package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.PostResponse;
import com.sashank.paraspa.entity.Comment;
import com.sashank.paraspa.entity.Follow;
import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.entity.PostLike;
import com.sashank.paraspa.entity.PostMedia;
import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.SkillCategory;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.repository.FeedQuery;
import com.sashank.paraspa.repository.TrendingPostQuery;
import com.sashank.paraspa.service.PostAssembler;
import com.sashank.paraspa.support.CountingDataSourceConfig;
import com.sashank.paraspa.support.StatementCounter;
import com.sashank.paraspa.util.CursorUtil;
import com.sashank.paraspa.util.DtoMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real feed and explore queries (not mocks) on an in-memory database: ordering, exclusions, cursor
 * paging, and the number of SQL statements per page. The SQL used here is also what runs on PostgreSQL; the
 * PostgreSQL-specific performance work (indexes, plans) is covered in the README and migration V7.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false"
})
@Import({FeedServiceImpl.class, PostAssembler.class, FeedQuery.class, TrendingPostQuery.class, DtoMapper.class,
        CountingDataSourceConfig.class})
class FeedServiceImplIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Autowired private FeedServiceImpl feedService;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;

    private User me, alice, bob, carol, dave;
    private Skill guitar, piano;
    private final StatementCounter counter = CountingDataSourceConfig.COUNTER;

    @BeforeEach
    void setUp() {
        feedService.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        me = user("me");
        alice = user("alice");
        bob = user("bob");
        carol = user("carol");
        dave = user("dave");
        guitar = skill("Guitar");
        piano = skill("Piano");
    }

    // ------------------------------------------------------------------ helpers

    private User user(String name) {
        User u = User.builder().name(name).email(name + "@example.com").password("x").build();
        em.persist(u);
        return u;
    }

    private Skill skill(String name) {
        Skill s = Skill.builder().name(name).category(SkillCategory.MUSIC).build();
        em.persist(s);
        return s;
    }

    private void follow(User follower, User followed) {
        em.persist(Follow.builder().follower(follower).following(followed).build());
    }

    /** Creates a post and back-dates it ({@code createdAt} is otherwise set to the real "now" on insert). */
    private Post post(User author, String content, LocalDateTime createdAt, long likes, long comments, Skill... tags) {
        Post p = Post.builder().author(author).content(content).likeCount(likes).commentCount(comments).build();
        for (Skill tag : tags) {
            p.getSkills().add(tag);
        }
        em.persist(p);
        em.flush();
        jdbc.update("update posts set created_at = ? where id = ?", Timestamp.valueOf(createdAt), p.getId());
        return p;
    }

    private void addMedia(Post post, int count) {
        for (int i = 0; i < count; i++) {
            post.getMedia().add(PostMedia.builder().post(post).url("/files/posts/" + post.getId() + "-" + i + ".png").position(i).build());
        }
        em.persist(post);
    }

    /** Forget everything in memory so the measured call behaves like a fresh request. */
    private void freshRequest() {
        em.flush();
        em.clear();
        counter.reset();
    }

    private static List<Long> ids(CursorPageResponse<PostResponse> page) {
        return page.getItems().stream().map(PostResponse::getId).toList();
    }

    private List<Long> walkFeed(User viewer, int size) {
        List<Long> seen = new ArrayList<>();
        String cursor = null;
        int guard = 0;
        do {
            CursorPageResponse<PostResponse> page = feedService.getFeed(viewer.getId(), cursor, size);
            seen.addAll(ids(page));
            cursor = page.getNextCursor();
        } while (cursor != null && ++guard < 50);
        return seen;
    }

    // ================================================================= FEED

    @Test
    void feed_returnsMyPostsAndPeopleIFollow_newestFirst_andNobodyElse() {
        follow(me, alice);
        follow(me, bob);
        Post a1 = post(alice, "a1", NOW_LOCAL.minusHours(5), 0, 0);
        Post b1 = post(bob, "b1", NOW_LOCAL.minusHours(4), 0, 0);
        Post mine = post(me, "mine", NOW_LOCAL.minusHours(3), 0, 0);
        Post a2 = post(alice, "a2", NOW_LOCAL.minusHours(1), 0, 0);
        post(carol, "not followed", NOW_LOCAL.minusMinutes(5), 99, 99);   // newest and popular, but not followed
        freshRequest();

        CursorPageResponse<PostResponse> page = feedService.getFeed(me.getId(), null, 20);

        assertThat(ids(page)).containsExactly(a2.getId(), mine.getId(), b1.getId(), a1.getId());
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void feed_doesNotIncludePostsOfPeopleWhoFollowMeUnlessIFollowThemBack() {
        follow(alice, me);   // alice follows me, I do not follow alice
        post(alice, "alice post", NOW_LOCAL.minusHours(1), 0, 0);
        Post mine = post(me, "mine", NOW_LOCAL.minusHours(2), 0, 0);
        freshRequest();

        assertThat(ids(feedService.getFeed(me.getId(), null, 20))).containsExactly(mine.getId());
    }

    @Test
    void feed_isEmptyForANewUserWithNoPostsAndNoFollows() {
        freshRequest();

        CursorPageResponse<PostResponse> page = feedService.getFeed(me.getId(), null, 20);

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void feed_pagesWithACursor_visitingEveryPostOnce_evenWhenPostsShareATimestamp() {
        follow(me, alice);
        follow(me, bob);
        LocalDateTime same = NOW_LOCAL.minusHours(2);
        List<Post> created = new ArrayList<>();
        created.add(post(alice, "p1", NOW_LOCAL.minusHours(9), 0, 0));
        created.add(post(bob, "p2", same, 0, 0));          // p2..p5 share one timestamp (ids break the tie)
        created.add(post(alice, "p3", same, 0, 0));
        created.add(post(bob, "p4", same, 0, 0));
        created.add(post(me, "p5", same, 0, 0));
        created.add(post(alice, "p6", NOW_LOCAL.minusHours(1), 0, 0));
        created.add(post(bob, "p7", NOW_LOCAL.minusMinutes(10), 0, 0));
        freshRequest();

        // newest first; inside the shared timestamp, higher id first
        List<Long> expected = List.of(created.get(6).getId(), created.get(5).getId(),
                created.get(4).getId(), created.get(3).getId(), created.get(2).getId(), created.get(1).getId(),
                created.get(0).getId());

        for (int size : new int[]{1, 2, 3, 7, 20}) {
            assertThat(walkFeed(me, size)).as("page size %d", size).containsExactlyElementsOf(expected);
        }
    }

    @Test
    void feed_nextCursorIsNullOnTheLastPageAndPresentBeforeIt() {
        follow(me, alice);
        post(alice, "1", NOW_LOCAL.minusHours(3), 0, 0);
        post(alice, "2", NOW_LOCAL.minusHours(2), 0, 0);
        post(alice, "3", NOW_LOCAL.minusHours(1), 0, 0);
        freshRequest();

        assertThat(feedService.getFeed(me.getId(), null, 2).getNextCursor()).isNotNull();
        assertThat(feedService.getFeed(me.getId(), null, 3).getNextCursor()).isNull();   // exactly one full page: no phantom next page
        assertThat(feedService.getFeed(me.getId(), null, 4).getNextCursor()).isNull();
    }

    @Test
    void feed_aPostCreatedWhileScrollingDoesNotShiftTheLaterPages() {
        follow(me, alice);
        post(alice, "1", NOW_LOCAL.minusHours(4), 0, 0);
        Post p2 = post(alice, "2", NOW_LOCAL.minusHours(3), 0, 0);
        Post p3 = post(alice, "3", NOW_LOCAL.minusHours(2), 0, 0);
        post(alice, "4", NOW_LOCAL.minusHours(1), 0, 0);
        freshRequest();

        CursorPageResponse<PostResponse> first = feedService.getFeed(me.getId(), null, 2);   // 4, 3
        post(alice, "brand new", NOW_LOCAL.minusMinutes(1), 0, 0);                           // arrives mid-scroll
        freshRequest();
        CursorPageResponse<PostResponse> second = feedService.getFeed(me.getId(), first.getNextCursor(), 2);

        assertThat(ids(second)).containsExactly(p2.getId(), second.getItems().get(1).getId());
        assertThat(ids(second)).doesNotContain(p3.getId());   // no repeat of what page one showed
    }

    @Test
    void feed_rejectsABadCursorAndBadSizes() {
        assertThatThrownBy(() -> feedService.getFeed(me.getId(), "garbage!", 20))
                .isInstanceOf(BadRequestException.class).hasMessage("Invalid cursor");
        assertThatThrownBy(() -> feedService.getFeed(me.getId(), null, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> feedService.getFeed(me.getId(), null, 101)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void feed_eachItemCarriesAuthorMediaSkillTagsCountsAndLikedByMe() {
        follow(me, alice);
        Post p = post(alice, "hello", NOW_LOCAL.minusHours(1), 3, 2, guitar, piano);
        addMedia(p, 2);
        em.persist(PostLike.builder().post(p).user(me).build());
        Post other = post(alice, "not liked", NOW_LOCAL.minusHours(2), 0, 0);
        freshRequest();

        List<PostResponse> items = feedService.getFeed(me.getId(), null, 20).getItems();

        PostResponse liked = items.get(0);
        assertThat(liked.getId()).isEqualTo(p.getId());
        assertThat(liked.getAuthor().getName()).isEqualTo("alice");
        assertThat(liked.getAuthor().getEmail()).isNull();                       // author summary, never the email
        assertThat(liked.getContent()).isEqualTo("hello");
        assertThat(liked.getSkills()).extracting("name").containsExactlyInAnyOrder("Guitar", "Piano");
        assertThat(liked.getMedia()).extracting("position").containsExactly(0, 1);
        assertThat(liked.getMedia()).extracting("url").containsExactly(
                "/files/posts/" + p.getId() + "-0.png", "/files/posts/" + p.getId() + "-1.png");
        assertThat(liked.getLikeCount()).isEqualTo(3);
        assertThat(liked.getCommentCount()).isEqualTo(2);
        assertThat(liked.isLikedByMe()).isTrue();
        assertThat(items.get(1).getId()).isEqualTo(other.getId());
        assertThat(items.get(1).isLikedByMe()).isFalse();
    }

    @Test
    void feed_likedByMeIsAboutTheViewer_notAboutTheAuthorOrOthers() {
        follow(me, alice);
        follow(bob, alice);
        Post p = post(alice, "x", NOW_LOCAL.minusHours(1), 1, 0);
        em.persist(PostLike.builder().post(p).user(bob).build());   // bob likes it, I do not
        freshRequest();

        assertThat(feedService.getFeed(me.getId(), null, 20).getItems().get(0).isLikedByMe()).isFalse();
        freshRequest();
        assertThat(feedService.getFeed(bob.getId(), null, 20).getItems().get(0).isLikedByMe()).isTrue();
    }

    // ============================================================ NO N+1 (measured)

    private void populateManyPosts(int posts, int authors) {
        populateManyPosts("author", posts, authors);
    }

    private void populateManyPosts(String namePrefix, int posts, int authors) {
        List<User> people = IntStream.range(0, authors).mapToObj(i -> user(namePrefix + i)).toList();
        people.forEach(u -> follow(me, u));
        for (int i = 0; i < posts; i++) {
            Post p = post(people.get(i % authors), "post " + i, NOW_LOCAL.minusMinutes(i + 1), i, i % 3, guitar, piano);
            addMedia(p, 1 + (i % 4));
            if (i % 2 == 0) {
                em.persist(PostLike.builder().post(p).user(me).build());
            }
        }
    }

    @Test
    void feed_runsAFixedNumberOfStatementsPerPage_whateverThePageSize() {
        populateManyPosts(60, 20);

        int[] sizes = {3, 10, 30, 60};
        List<Integer> statementCounts = new ArrayList<>();
        for (int size : sizes) {
            freshRequest();
            int returned = feedService.getFeed(me.getId(), null, size).getItems().size();
            statementCounts.add(counter.count());
            assertThat(returned).isEqualTo(size);
        }

        // feed ids (1), posts+authors (1), skill tags (1), media (1), "which did I like" (1) = 5, for 3 posts or 60
        assertThat(statementCounts).as("statements per page for sizes %s", List.of(3, 10, 30, 60))
                .containsOnly(5);
    }

    @Test
    void feed_neverLoadsAnAuthorOrACollectionOneRowAtATime() {
        populateManyPosts(40, 20);
        freshRequest();

        feedService.getFeed(me.getId(), null, 40);

        // an N+1 shows up as the same statement shape repeated; after batching every table is touched once
        List<String> sql = counter.statements().stream().map(String::toLowerCase).toList();
        assertThat(sql).hasSize(5);
        assertThat(sql.stream().filter(s -> s.contains("from users") && !s.contains("from posts")).count())
                .as("no statement loads authors on their own").isZero();
        assertThat(sql.stream().filter(s -> s.contains("from posts")).count()).isEqualTo(2);   // id query + posts with authors
        assertThat(sql.stream().filter(s -> s.contains("join users")).count()).isEqualTo(1);   // authors fetched with the posts
        assertThat(sql.stream().filter(s -> s.contains("post_media")).count()).isEqualTo(1);
        assertThat(sql.stream().filter(s -> s.contains("post_skills")).count()).isEqualTo(1);
        assertThat(sql.stream().filter(s -> s.contains("post_likes")).count()).isEqualTo(1);
    }

    @Test
    void feed_statementCountDoesNotGrowWithTheNumberOfDistinctAuthors() {
        populateManyPosts("few", 30, 2);
        freshRequest();
        feedService.getFeed(me.getId(), null, 30);
        int fewAuthors = counter.count();

        // a bigger feed whose 30 newest posts come from 30 different authors
        populateManyPosts("many", 30, 30);
        freshRequest();
        List<PostResponse> page = feedService.getFeed(me.getId(), null, 30).getItems();
        assertThat(page.stream().map(r -> r.getAuthor().getId()).distinct().count())
                .as("the measured page really mixes many different authors").isGreaterThan(10);

        assertThat(counter.count()).isEqualTo(fewAuthors);
    }

    // ================================================================= EXPLORE

    private Post hot(User author, String content, long hoursOld, long likes, long comments, Skill... tags) {
        return post(author, content, NOW_LOCAL.minusHours(hoursOld), likes, comments, tags);
    }

    @Test
    void explore_ranksByEngagementWithTimeDecay_andWeighsACommentAsTwoLikes() {
        Post commentsPost = hot(dave, "10 comments, 10h old", 10, 0, 10);    // 20 / 12^1.5  = 0.481
        Post newModest = hot(carol, "3 likes, 2h old", 2, 3, 0);             //  3 /  4^1.5  = 0.375
        Post tenLikes = hot(carol, "10 likes, 10h old", 10, 10, 0);          // 10 / 12^1.5  = 0.241
        Post oldHot = hot(bob, "100 likes, 6 days old", 6 * 24, 100, 0);     // 100 / 146^1.5 = 0.057
        Post zero = hot(dave, "nobody reacted", 1, 0, 0);                    // 0
        freshRequest();

        List<Long> order = ids(feedService.getExplore(me.getId(), null, null, 20));

        assertThat(order).containsExactly(commentsPost.getId(), newModest.getId(), tenLikes.getId(), oldHot.getId(), zero.getId());
    }

    @Test
    void explore_aFreshPostOutranksAnOlderPostWithMoreLikes_thatIsTheDecay() {
        Post fresh = hot(carol, "fresh", 1, 5, 0);       //   5 /  3^1.5 = 0.962
        Post older = hot(bob, "older but bigger", 48, 30, 0);   // 30 / 50^1.5 = 0.085
        freshRequest();

        assertThat(ids(feedService.getExplore(me.getId(), null, null, 20))).containsExactly(fresh.getId(), older.getId());
    }

    @Test
    void explore_excludesPeopleIFollow_myOwnPosts_andPostsOlderThanSevenDays() {
        follow(me, alice);
        hot(alice, "followed and hot", 1, 500, 500);               // excluded: I follow alice
        hot(me, "mine and hot", 1, 500, 500);                      // excluded: my own
        hot(bob, "8 days old", 8 * 24, 9999, 9999);                // excluded: outside the window
        hot(bob, "just outside, 7d + 1h", 7 * 24 + 1, 9999, 9999); // excluded
        Post inside = hot(bob, "6d 23h old", 7 * 24 - 1, 50, 0);   // included: still inside the window
        freshRequest();

        assertThat(ids(feedService.getExplore(me.getId(), null, null, 20))).containsExactly(inside.getId());
    }

    @Test
    void explore_followingSomeoneRemovesTheirPostsFromExplore() {
        Post bobs = hot(bob, "bob's", 2, 10, 0);
        Post carols = hot(carol, "carol's", 2, 5, 0);
        freshRequest();
        assertThat(ids(feedService.getExplore(me.getId(), null, null, 20))).containsExactly(bobs.getId(), carols.getId());

        follow(me, bob);
        freshRequest();
        assertThat(ids(feedService.getExplore(me.getId(), null, null, 20))).containsExactly(carols.getId());
    }

    @Test
    void explore_filtersBySkillTag() {
        Post tagged = hot(bob, "guitar post", 2, 4, 0, guitar);
        hot(carol, "piano post", 2, 40, 0, piano);
        hot(dave, "untagged", 2, 40, 0);
        Post alsoTagged = hot(carol, "guitar and piano", 5, 1, 0, guitar, piano);
        freshRequest();

        assertThat(ids(feedService.getExplore(me.getId(), guitar.getId(), null, 20)))
                .containsExactly(tagged.getId(), alsoTagged.getId());
        assertThat(ids(feedService.getExplore(me.getId(), 999_999L, null, 20))).isEmpty();
    }

    @Test
    void explore_isEmptyWhenThereIsNothingToShow() {
        freshRequest();

        CursorPageResponse<PostResponse> page = feedService.getExplore(me.getId(), null, null, 20);

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void explore_postsWithTheSameScoreAreOrderedByIdDescending() {
        Post first = hot(bob, "a", 3, 0, 0);
        Post second = hot(carol, "b", 3, 0, 0);
        Post third = hot(dave, "c", 3, 0, 0);
        freshRequest();

        assertThat(ids(feedService.getExplore(me.getId(), null, null, 20)))
                .containsExactly(third.getId(), second.getId(), first.getId());
    }

    private List<Long> walkExplore(int size, Long skillId) {
        List<Long> seen = new ArrayList<>();
        String cursor = null;
        int guard = 0;
        do {
            CursorPageResponse<PostResponse> page = feedService.getExplore(me.getId(), skillId, cursor, size);
            seen.addAll(ids(page));
            cursor = page.getNextCursor();
        } while (cursor != null && ++guard < 50);
        return seen;
    }

    @Test
    void explore_cursorPagingVisitsEveryPostOnceInRankOrder_forAnyPageSize() {
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < 12; i++) {   // a mix of scores, including ties (equal likes at the same age)
            posts.add(hot(i % 2 == 0 ? bob : carol, "p" + i, 2 + (i % 4), i % 5, i % 3));
        }
        freshRequest();
        List<Long> fullOrder = ids(feedService.getExplore(me.getId(), null, null, 100));
        assertThat(fullOrder).hasSize(12).doesNotHaveDuplicates();

        for (int size : new int[]{1, 2, 3, 5, 11, 12}) {
            assertThat(walkExplore(size, null)).as("page size %d", size).containsExactlyElementsOf(fullOrder);
        }
    }

    @Test
    void explore_theRankingInstantIsPinnedByTheCursor_soLaterPagesStayConsistent() {
        Post a = hot(bob, "a", 3, 40, 0);
        Post b = hot(carol, "b", 4, 30, 0);
        Post oldest = hot(dave, "6 days 20h old, only inside the window right now", 6 * 24 + 20, 500, 0);
        freshRequest();
        CursorPageResponse<PostResponse> first = feedService.getExplore(me.getId(), null, null, 1);
        assertThat(first.getItems()).hasSize(1);

        // five hours pass between page 1 and page 2: "oldest" is now >7 days old, but this scroll ranks as of page 1
        feedService.setClock(Clock.fixed(NOW.plusSeconds(5 * 3600), ZoneOffset.UTC));
        List<Long> seen = new ArrayList<>(ids(first));
        String cursor = first.getNextCursor();
        while (cursor != null) {
            CursorPageResponse<PostResponse> page = feedService.getExplore(me.getId(), null, cursor, 1);
            seen.addAll(ids(page));
            cursor = page.getNextCursor();
        }

        assertThat(seen).containsExactlyInAnyOrder(a.getId(), b.getId(), oldest.getId());
        // a fresh scroll started now no longer includes the post that aged out of the window
        freshRequest();
        assertThat(ids(feedService.getExplore(me.getId(), null, null, 20))).doesNotContain(oldest.getId());
    }

    @Test
    void explore_pagingWithASkillFilterOnlyReturnsTaggedPosts() {
        for (int i = 0; i < 6; i++) {
            hot(bob, "tagged " + i, 2 + i, 5, 0, guitar);
            hot(carol, "untagged " + i, 2 + i, 50, 0);
        }
        freshRequest();

        List<Long> walked = walkExplore(2, guitar.getId());

        assertThat(walked).hasSize(6).doesNotHaveDuplicates();
        assertThat(walked).allSatisfy(id -> assertThat(jdbc.queryForObject(
                "select count(*) from post_skills where post_id = ? and skill_id = ?", Integer.class, id, guitar.getId())).isEqualTo(1));
    }

    @Test
    void explore_rejectsBadCursorsAndSizes() {
        assertThatThrownBy(() -> feedService.getExplore(me.getId(), null, "garbage!", 20))
                .isInstanceOf(BadRequestException.class).hasMessage("Invalid cursor");
        assertThatThrownBy(() -> feedService.getExplore(me.getId(), null, CursorUtil.encode(5L), 20))   // a feed/id cursor is not an explore cursor
                .isInstanceOf(BadRequestException.class);
        String fromTheFuture = CursorUtil.encodeScore(NOW.getEpochSecond() + 3600, 1.0, 5L);
        assertThatThrownBy(() -> feedService.getExplore(me.getId(), null, fromTheFuture, 20))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> feedService.getExplore(me.getId(), null, null, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> feedService.getExplore(me.getId(), null, null, 101)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void explore_eachItemCarriesAuthorMediaSkillTagsCountsAndLikedByMe() {
        Post p = hot(bob, "trending", 2, 4, 1, guitar);
        addMedia(p, 3);
        em.persist(PostLike.builder().post(p).user(me).build());
        freshRequest();

        PostResponse item = feedService.getExplore(me.getId(), null, null, 20).getItems().get(0);

        assertThat(item.getAuthor().getName()).isEqualTo("bob");
        assertThat(item.getAuthor().getEmail()).isNull();
        assertThat(item.getMedia()).hasSize(3);
        assertThat(item.getSkills()).extracting("name").containsExactly("Guitar");
        assertThat(item.getLikeCount()).isEqualTo(4);
        assertThat(item.getCommentCount()).isEqualTo(1);
        assertThat(item.isLikedByMe()).isTrue();
    }

    @Test
    void explore_runsAFixedNumberOfStatementsPerPage_whateverThePageSize() {
        List<User> people = IntStream.range(0, 20).mapToObj(i -> user("author" + i)).toList();
        for (int i = 0; i < 60; i++) {
            Post p = hot(people.get(i % 20), "post " + i, 1 + (i % 100), i, i % 3, guitar, piano);
            addMedia(p, 1 + (i % 4));
            if (i % 2 == 0) {
                em.persist(PostLike.builder().post(p).user(me).build());
            }
        }

        List<Integer> statementCounts = new ArrayList<>();
        for (int size : new int[]{3, 10, 30, 60}) {
            freshRequest();
            assertThat(feedService.getExplore(me.getId(), null, null, size).getItems()).hasSize(size);
            statementCounts.add(counter.count());
        }

        // ranking query (1), posts+authors (1), skill tags (1), media (1), "which did I like" (1) = 5
        assertThat(statementCounts).containsOnly(5);
    }

    @Test
    void commentsDoNotNeedToExistForCommentCountToRank_theStoredCounterIsUsed() {
        // explore ranks on the stored counters; it does not count comment rows (that would be one query per post)
        Post p = hot(bob, "counter only", 2, 0, 5);
        em.persist(Comment.builder().post(p).author(carol).content("one real row").build());
        freshRequest();

        PostResponse item = feedService.getExplore(me.getId(), null, null, 20).getItems().get(0);

        assertThat(item.getCommentCount()).isEqualTo(5);   // the stored counter, not the single row
    }
}
