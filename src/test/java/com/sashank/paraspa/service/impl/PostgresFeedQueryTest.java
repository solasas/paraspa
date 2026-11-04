package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.PostResponse;
import com.sashank.paraspa.entity.Block;
import com.sashank.paraspa.entity.Follow;
import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.FollowStatus;
import com.sashank.paraspa.repository.BlockRepository;
import com.sashank.paraspa.repository.FollowRepository;
import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.enums.SkillCategory;
import com.sashank.paraspa.repository.PostRepository;
import com.sashank.paraspa.repository.SkillRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.FeedService;
import com.sashank.paraspa.support.PostgresSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The feed and explore queries on real PostgreSQL (native SQL: row-value cursors, POWER/EXTRACT, anti-joins), with the
 * real Flyway schema. The H2 tests cover the logic quickly; this proves the SQL is valid and equivalent on the
 * production database. Skipped when no PostgreSQL is available.
 */
@EnabledIf("com.sashank.paraspa.support.PostgresSupport#available")
@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.com.sashank.paraspa=WARN"})
class PostgresFeedQueryTest {

    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        PostgresSupport.register(registry);
    }

    @Autowired private FeedService feedService;
    @Autowired private UserRepository userRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private FollowRepository followRepository;
    @Autowired private BlockRepository blockRepository;
    @Autowired private SkillRepository skillRepository;
    @Autowired private JdbcTemplate jdbc;

    private User user(String name, boolean isPrivate) {
        return userRepository.save(User.builder().name(name).email(name + UNIQUE.incrementAndGet() + System.nanoTime() + "@example.com")
                .password("x").isPrivate(isPrivate).build());
    }

    private User user(String name) {
        return user(name, false);
    }

    private void follow(User follower, User following, FollowStatus status) {
        followRepository.save(Follow.builder().follower(follower).following(following).status(status).build());
    }

    private Post post(User author, LocalDateTime createdAt, long likes) {
        Post p = postRepository.save(Post.builder().author(author).content("p").likeCount(likes).build());
        jdbc.update("UPDATE posts SET created_at = ? WHERE id = ?", Timestamp.valueOf(createdAt), p.getId());
        return p;
    }

    /** A skill used only by one test, so explore (which is global) can be narrowed to that test's posts. */
    private Skill ownSkill() {
        return skillRepository.save(Skill.builder().name("Skill" + UNIQUE.incrementAndGet() + System.nanoTime()).category(SkillCategory.OTHER).build());
    }

    private Post taggedPost(User author, LocalDateTime createdAt, long likes, Skill skill) {
        Post p = Post.builder().author(author).content("p").likeCount(likes).build();
        p.getSkills().add(skill);
        p = postRepository.save(p);
        jdbc.update("UPDATE posts SET created_at = ? WHERE id = ?", Timestamp.valueOf(createdAt), p.getId());
        return p;
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
        } while (cursor != null && ++guard < 100);
        return seen;
    }

    // ================================================================ feed

    @Test
    void feed_isNewestFirst_andOnlyMyPostsAndPeopleIFollowWithAnAcceptedFollow() {
        LocalDateTime now = LocalDateTime.now();
        User me = user("me"), alice = user("alice"), stranger = user("stranger"), pia = user("pia", true), ben = user("ben");
        follow(me, alice, FollowStatus.ACCEPTED);
        follow(me, pia, FollowStatus.PENDING);      // still waiting: her posts stay hidden
        follow(me, ben, FollowStatus.ACCEPTED);
        blockRepository.save(Block.builder().blocker(me).blocked(ben).build());   // blocked later: hidden even though followed once

        Post a1 = post(alice, now.minusHours(3), 0);
        Post mine = post(me, now.minusHours(2), 0);
        Post a2 = post(alice, now.minusHours(1), 0);
        post(stranger, now.minusMinutes(1), 50);
        post(pia, now.minusMinutes(2), 50);
        post(ben, now.minusMinutes(3), 50);

        assertThat(ids(feedService.getFeed(me.getId(), null, 20))).containsExactly(a2.getId(), mine.getId(), a1.getId());
    }

    @Test
    void feed_cursorPaging_neverSkipsOrRepeats_evenForPostsWithTheSameTimestamp() {
        LocalDateTime sameInstant = LocalDateTime.now().minusMinutes(10);
        User me = user("me"), alice = user("alice");
        follow(me, alice, FollowStatus.ACCEPTED);
        List<Long> all = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            all.add(post(i % 2 == 0 ? alice : me, i < 15 ? sameInstant : sameInstant.plusSeconds(i), 0).getId());   // 15 ties
        }

        List<Long> walked = walkFeed(me, 4);

        assertThat(walked).hasSize(25).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(all);
    }

    @Test
    void feed_acceptedFollowBecomesVisible_andABlockHidesItAgain() {
        User me = user("me"), pia = user("pia", true);
        follow(me, pia, FollowStatus.PENDING);
        Post secret = post(pia, LocalDateTime.now().minusMinutes(5), 0);
        assertThat(ids(feedService.getFeed(me.getId(), null, 20))).doesNotContain(secret.getId());

        jdbc.update("UPDATE follows SET status = 'ACCEPTED' WHERE follower_id = ? AND following_id = ?", me.getId(), pia.getId());
        assertThat(ids(feedService.getFeed(me.getId(), null, 20))).contains(secret.getId());

        blockRepository.save(Block.builder().blocker(pia).blocked(me).build());   // she blocks me: symmetric
        assertThat(ids(feedService.getFeed(me.getId(), null, 20))).doesNotContain(secret.getId());
    }

    // ================================================================ explore (trending)

    @Test
    void explore_ranksByEngagementAndAge_andExcludesFollowedBlockedAndPrivateAuthors() {
        LocalDateTime now = LocalDateTime.now();
        Skill skill = ownSkill();
        User me = user("me"), popular = user("popular"), quiet = user("quiet"), followed = user("followed"),
                blocked = user("blocked"), pia = user("pia", true);
        follow(me, followed, FollowStatus.ACCEPTED);
        blockRepository.save(Block.builder().blocker(me).blocked(blocked).build());

        Post hot = taggedPost(popular, now.minusHours(30), 40, skill);
        Post cold = taggedPost(quiet, now.minusHours(30), 1, skill);
        taggedPost(followed, now.minusHours(30), 90, skill);
        taggedPost(blocked, now.minusHours(30), 90, skill);
        taggedPost(pia, now.minusHours(30), 90, skill);
        taggedPost(me, now.minusHours(30), 90, skill);

        CursorPageResponse<PostResponse> page = feedService.getExplore(me.getId(), skill.getId(), null, 100);

        assertThat(ids(page)).containsExactly(hot.getId(), cold.getId());   // best first; the four excluded authors are absent
    }

    @Test
    void explore_cursorPaging_walksEveryPostOnce() {
        LocalDateTime now = LocalDateTime.now();
        Skill skill = ownSkill();
        User me = user("me"), author = user("author");
        List<Long> mine = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            mine.add(taggedPost(author, now.minusHours(2 + i), i % 4, skill).getId());
        }

        List<Long> seen = new ArrayList<>();
        String cursor = null;
        int guard = 0;
        do {
            CursorPageResponse<PostResponse> page = feedService.getExplore(me.getId(), skill.getId(), cursor, 5);
            seen.addAll(ids(page));
            cursor = page.getNextCursor();
        } while (cursor != null && ++guard < 50);

        assertThat(seen).hasSize(12).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(mine);
    }
}
