package com.sashank.paraspa.e2e;

import com.sashank.paraspa.dto.response.AuthResponse;
import com.sashank.paraspa.repository.BlockRepository;
import com.sashank.paraspa.repository.PostLikeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * The safety rules through the real HTTP stack (filter, controllers, services, queries) on an in-memory database:
 * blocks, reports, private accounts, and the admin role. The main point: once someone is blocked, their content is
 * missing from EVERY listing, in both directions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:safety_e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "logging.level.root=WARN",
        "logging.level.com.sashank.paraspa=WARN"
})
class SafetyEndToEndTest {

    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;

    /** H2 has no {@code ON CONFLICT DO NOTHING}; these two native inserts are swapped for portable equivalents. */
    @SpyBean private PostLikeRepository postLikeRepository;
    @SpyBean private BlockRepository blockRepository;

    private record Person(long id, String name, String token) {
    }

    @BeforeEach
    void portableInserts() {
        doAnswer(i -> jdbc.update(
                "INSERT INTO post_likes (post_id, user_id, created_at) SELECT ?, ?, ? WHERE NOT EXISTS "
                        + "(SELECT 1 FROM post_likes WHERE post_id = ? AND user_id = ?)",
                i.getArgument(0), i.getArgument(1), Timestamp.valueOf((LocalDateTime) i.getArgument(2)),
                i.getArgument(0), i.getArgument(1)))
                .when(postLikeRepository).insertIfAbsent(any(), any(), any());
        doAnswer(i -> jdbc.update(
                "INSERT INTO blocks (blocker_id, blocked_id, created_at) SELECT ?, ?, ? WHERE NOT EXISTS "
                        + "(SELECT 1 FROM blocks WHERE blocker_id = ? AND blocked_id = ?)",
                i.getArgument(0), i.getArgument(1), Timestamp.valueOf((LocalDateTime) i.getArgument(2)),
                i.getArgument(0), i.getArgument(1)))
                .when(blockRepository).insertIfAbsent(any(), any(), any());
    }

    // ------------------------------------------------------------------ helpers

    private Person register(String name) {
        String unique = name + UNIQUE.incrementAndGet();
        ResponseEntity<AuthResponse> r = rest.postForEntity("/api/auth/register",
                Map.of("name", unique, "email", unique.toLowerCase() + "@example.com", "password", "secret123"), AuthResponse.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new Person(r.getBody().getId(), unique, r.getBody().getToken());
    }

    /** Registers a user, then makes them an admin directly in the database (there is no endpoint for that). */
    private Person admin() {
        Person p = register("Admin");
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", p.id());
        return p;
    }

    private ResponseEntity<Object> call(HttpMethod method, String path, Person as, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (as != null) {
            headers.setBearerAuth(as.token());
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), Object.class);
    }

    private ResponseEntity<Object> get(String path, Person as) {
        return call(HttpMethod.GET, path, as, null);
    }

    private HttpStatus status(ResponseEntity<?> r) {
        return HttpStatus.valueOf(r.getStatusCode().value());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(ResponseEntity<Object> r) {
        return (Map<String, Object>) r.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(ResponseEntity<Object> r) {
        assertThat(r.getStatusCode().is2xxSuccessful()).as("body was %s", r.getBody()).isTrue();
        return (List<Map<String, Object>>) r.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(ResponseEntity<Object> r) {
        assertThat(r.getStatusCode().is2xxSuccessful()).as("body was %s", r.getBody()).isTrue();
        return (List<Map<String, Object>>) map(r).get("items");
    }

    private long id(Map<String, Object> m) {
        return ((Number) m.get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private long authorId(Map<String, Object> post) {
        return id((Map<String, Object>) post.get("author"));
    }

    private List<Long> authorIds(List<Map<String, Object>> posts) {
        return posts.stream().map(this::authorId).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Long> userIds(List<Map<String, Object>> matches) {
        return matches.stream().map(m -> id((Map<String, Object>) m.get("user"))).toList();
    }

    private long createSkill(String name) {
        return id(map(call(HttpMethod.POST, "/api/skills", register("Skiller"),
                Map.of("name", name + UNIQUE.incrementAndGet(), "category", "MUSIC"))));
    }

    private void addSkill(Person p, long skillId, String type) {
        assertThat(status(call(HttpMethod.POST, "/api/profile/skills", p,
                Map.of("skillId", skillId, "type", type, "level", "BEGINNER")))).isEqualTo(HttpStatus.CREATED);
    }

    private long post(Person p, String content, Long skillId) {
        Map<String, Object> body = skillId == null ? Map.of("content", content) : Map.of("content", content, "skillIds", List.of(skillId));
        ResponseEntity<Object> r = call(HttpMethod.POST, "/api/posts", p, body);
        assertThat(r.getStatusCode()).as("body was %s", r.getBody()).isEqualTo(HttpStatus.CREATED);
        return id(map(r));
    }

    /** Explore ranks "as of the current whole second", so a post made in this very second is not ranked yet. */
    private void letPostsAge() {
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void block(Person who, Person target) {
        assertThat(status(call(HttpMethod.PUT, "/api/users/" + target.id() + "/block", who, null))).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private void follow(Person who, Person target) {
        assertThat(status(call(HttpMethod.PUT, "/api/users/" + target.id() + "/follow", who, null)).is2xxSuccessful()).isTrue();
    }

    private void makePrivate(Person p) {
        assertThat(status(call(HttpMethod.PUT, "/api/profile/me", p, Map.of("isPrivate", true)))).isEqualTo(HttpStatus.OK);
    }

    // ================================================================ a blocked user disappears from every listing

    @Test
    void blockedUsersContent_disappearsFromEveryListing_inBothDirections() {
        long guitar = createSkill("Guitar");
        long piano = createSkill("Piano");
        Person me = register("Me");
        Person blocked = register("Blocked");       // I block this one
        Person blocker = register("Blocker");       // this one blocks me
        Person fine = register("Fine");
        Person stranger = register("Stranger");   // not followed by me, so explore (which skips followed people) can show it
        // I teach guitar and want piano; everybody else is the opposite, so all of them match me (and each other not at all)
        addSkill(me, guitar, "TEACH");
        addSkill(me, piano, "LEARN");
        for (Person p : List.of(blocked, blocker, fine, stranger)) {
            addSkill(p, piano, "TEACH");
            addSkill(p, guitar, "LEARN");
        }
        follow(me, fine);
        follow(me, blocked);
        follow(blocked, me);
        follow(blocker, me);
        follow(me, blocker);

        long finePost = post(fine, "from fine", guitar);
        post(stranger, "from stranger", guitar);
        long blockedPost = post(blocked, "from blocked", guitar);
        long blockerPost = post(blocker, "from blocker", guitar);
        long myPost = post(me, "from me", guitar);
        // comments and likes on MY post, by everybody
        for (Person p : List.of(fine, blocked, blocker)) {
            assertThat(status(call(HttpMethod.POST, "/api/posts/" + myPost + "/comments", p, Map.of("content", "hi from " + p.name())))).isEqualTo(HttpStatus.CREATED);
            assertThat(status(call(HttpMethod.PUT, "/api/posts/" + myPost + "/like", p, null)).is2xxSuccessful()).isTrue();
        }

        block(me, blocked);
        block(blocker, me);
        letPostsAge();

        // sanity: before the later assertions, users who are not blocked are still there
        assertThat(authorIds(items(get("/api/feed", me)))).contains(fine.id()).doesNotContain(blocked.id(), blocker.id());
        assertThat(authorIds(items(get("/api/explore?skillId=" + guitar, me)))).contains(stranger.id()).doesNotContain(blocked.id(), blocker.id());
        assertThat(authorIds(list(get("/api/posts?size=100", me)))).contains(fine.id()).doesNotContain(blocked.id(), blocker.id());
        assertThat(authorIds(list(get("/api/posts/feed?size=100", me)))).doesNotContain(blocked.id(), blocker.id());
        assertThat(userIds(list(get("/api/matches", me)))).contains(fine.id()).doesNotContain(blocked.id(), blocker.id());
        assertThat(userIds(list(get("/api/matches/mutual", me)))).doesNotContain(blocked.id(), blocker.id());
        assertThat(list(get("/api/users/search?skillName=Piano&size=100", me)).stream().map(this::id).toList())
                .contains(fine.id()).doesNotContain(blocked.id(), blocker.id());
        assertThat(userIds(list(get("/api/explore/people", me)))).doesNotContain(blocked.id(), blocker.id());

        // comments and likes under my post: neither the user I blocked nor the one who blocked me
        List<String> commenters = items(get("/api/posts/" + myPost + "/comments", me)).stream().map(c -> ((Map<?, ?>) c.get("author")).get("name").toString()).toList();
        assertThat(commenters).containsExactly(fine.name());
        List<String> likers = list(get("/api/posts/" + myPost + "/likes?size=100", me)).stream().map(u -> u.get("name").toString()).toList();
        assertThat(likers).containsExactly(fine.name());

        // the same rules from the other side: the blocked user sees nothing of mine either
        assertThat(authorIds(items(get("/api/feed", blocked)))).doesNotContain(me.id());
        assertThat(authorIds(list(get("/api/posts?size=100", blocked)))).doesNotContain(me.id());
        assertThat(userIds(list(get("/api/matches", blocked)))).doesNotContain(me.id());
        assertThat(list(get("/api/users/search?skillName=Guitar&size=100", blocked)).stream().map(this::id).toList()).doesNotContain(me.id());
        assertThat(authorIds(items(get("/api/explore?skillId=" + guitar, blocker)))).doesNotContain(me.id());
        assertThat(authorIds(items(get("/api/explore?skillId=" + guitar, blocked)))).doesNotContain(me.id());

        // direct access: posts and profiles of a blocked user are simply "not found"
        assertThat(status(get("/api/posts/" + blockedPost, me))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(get("/api/posts/" + blockerPost, me))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(get("/api/posts/" + myPost, blocked))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(get("/api/users/" + blocked.id() + "/posts", me))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(get("/api/users/" + blocked.id(), me))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(get("/api/users/" + me.id(), blocker))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(get("/api/posts/" + finePost, me))).isEqualTo(HttpStatus.OK);

        // unblocking brings everything back (follows do not come back)
        assertThat(status(call(HttpMethod.DELETE, "/api/users/" + blocked.id() + "/block", me, null))).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(authorIds(list(get("/api/posts?size=100", me)))).contains(blocked.id());
        assertThat(userIds(list(get("/api/matches", me)))).contains(blocked.id());
        assertThat(status(get("/api/posts/" + blockedPost, me))).isEqualTo(HttpStatus.OK);
        assertThat(map(get("/api/users/" + blocked.id() + "/follow-status", me)).get("isFollowedByMe")).isEqualTo(false);
    }

    @Test
    void blocking_removesFollowsBothWays_andRefusesFollowMessageAndExchange_bothDirections() {
        long guitar = createSkill("Guitar");
        long piano = createSkill("Piano");
        Person a = register("A");
        Person b = register("B");
        addSkill(a, guitar, "TEACH");
        addSkill(a, piano, "LEARN");
        addSkill(b, piano, "TEACH");
        addSkill(b, guitar, "LEARN");
        follow(a, b);
        follow(b, a);

        block(a, b);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follows WHERE (follower_id=? AND following_id=?) OR (follower_id=? AND following_id=?)",
                Long.class, a.id(), b.id(), b.id(), a.id())).isZero();

        Map<String, Object> msg = Map.of("receiverId", 0, "content", "hello");
        for (Person[] pair : new Person[][]{{a, b}, {b, a}}) {
            Person from = pair[0], to = pair[1];
            assertThat(status(call(HttpMethod.PUT, "/api/users/" + to.id() + "/follow", from, null))).isIn(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND);
            assertThat(status(call(HttpMethod.POST, "/api/messages", from, Map.of("receiverId", to.id(), "content", "hello")))).isIn(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND);
            assertThat(status(call(HttpMethod.POST, "/api/exchanges", from,
                    Map.of("receiverId", to.id(), "offeredSkillId", from == a ? guitar : piano, "wantedSkillId", from == a ? piano : guitar)))).isIn(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND);
        }
        // the one who was blocked cannot tell the difference from a missing user
        assertThat(status(call(HttpMethod.PUT, "/api/users/" + a.id() + "/follow", b, null))).isEqualTo(HttpStatus.NOT_FOUND);
        // the blocker is told why
        assertThat(status(call(HttpMethod.PUT, "/api/users/" + b.id() + "/follow", a, null))).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void blockList_andBlockEdgeCases() {
        Person me = register("Me");
        Person other = register("Other");
        Person third = register("Third");

        assertThat(status(call(HttpMethod.PUT, "/api/users/" + me.id() + "/block", me, null))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(status(call(HttpMethod.PUT, "/api/users/999999/block", me, null))).isEqualTo(HttpStatus.NOT_FOUND);
        block(me, other);
        block(me, other);   // idempotent
        block(third, me);   // someone else blocked me: must not show in MY list

        List<Map<String, Object>> blocks = list(get("/api/blocks", me));
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).get("name")).isEqualTo(other.name());

        assertThat(status(call(HttpMethod.DELETE, "/api/users/" + other.id() + "/block", me, null))).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(status(call(HttpMethod.DELETE, "/api/users/" + other.id() + "/block", me, null))).isEqualTo(HttpStatus.NO_CONTENT);   // idempotent
        assertThat(list(get("/api/blocks", me))).isEmpty();
        assertThat(status(get("/api/blocks", null))).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    // ================================================================ private accounts

    @Test
    void privateAccount_followIsARequest_postsAreHiddenUntilAccepted_andRejectOrUnfollowRemovesIt() {
        Person owner = register("Owner");
        Person fan = register("Fan");
        Person stranger = register("Stranger");
        long secret = post(owner, "secret post", null);
        makePrivate(owner);

        // before any follow, only the owner sees it
        assertThat(status(get("/api/posts/" + secret, fan))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(get("/api/users/" + owner.id() + "/posts", fan))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authorIds(list(get("/api/posts?size=100", fan)))).doesNotContain(owner.id());
        assertThat(status(get("/api/posts/" + secret, owner))).isEqualTo(HttpStatus.OK);

        // a follow is now a pending request
        ResponseEntity<Object> followed = call(HttpMethod.PUT, "/api/users/" + owner.id() + "/follow", fan, null);
        assertThat(map(followed).get("status")).isEqualTo("PENDING");
        Map<String, Object> st = map(get("/api/users/" + owner.id() + "/follow-status", fan));
        assertThat(st.get("isFollowedByMe")).isEqualTo(false);
        assertThat(st.get("isFollowRequestedByMe")).isEqualTo(true);
        assertThat(status(get("/api/posts/" + secret, fan))).isEqualTo(HttpStatus.NOT_FOUND);   // pending is not enough
        assertThat(authorIds(items(get("/api/feed", fan)))).doesNotContain(owner.id());
        assertThat(map(get("/api/users/" + owner.id() + "/follow-status", owner)).get("followersCount")).isEqualTo(0);

        // only the owner can answer the request
        List<Map<String, Object>> requests = list(get("/api/follow-requests", owner));
        assertThat(requests).hasSize(1);
        long requestId = id(requests.get(0));
        assertThat(status(call(HttpMethod.PUT, "/api/follow-requests/" + requestId + "/accept", stranger, null))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(call(HttpMethod.PUT, "/api/follow-requests/" + requestId + "/accept", owner, null))).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(status(get("/api/posts/" + secret, fan))).isEqualTo(HttpStatus.OK);
        assertThat(authorIds(items(get("/api/feed", fan)))).contains(owner.id());
        assertThat(map(get("/api/users/" + owner.id() + "/follow-status", fan)).get("isFollowedByMe")).isEqualTo(true);
        assertThat(status(get("/api/posts/" + secret, stranger))).isEqualTo(HttpStatus.NOT_FOUND);   // others still locked out

        // unfollowing takes the access away again
        assertThat(status(call(HttpMethod.DELETE, "/api/users/" + owner.id() + "/follow", fan, null)).is2xxSuccessful()).isTrue();
        assertThat(status(get("/api/posts/" + secret, fan))).isEqualTo(HttpStatus.NOT_FOUND);

        // a rejected request never grants anything
        call(HttpMethod.PUT, "/api/users/" + owner.id() + "/follow", stranger, null);
        long second = id(list(get("/api/follow-requests", owner)).get(0));
        assertThat(status(call(HttpMethod.PUT, "/api/follow-requests/" + second + "/reject", owner, null))).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(list(get("/api/follow-requests", owner))).isEmpty();
        assertThat(status(get("/api/posts/" + secret, stranger))).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void privateAccount_isHiddenFromExplore_andGoingPublicAcceptsEveryWaitingRequest() {
        long skill = createSkill("Chess");
        Person owner = register("Owner");
        Person fan = register("Fan");
        post(owner, "private chess", skill);
        makePrivate(owner);
        call(HttpMethod.PUT, "/api/users/" + owner.id() + "/follow", fan, null);
        letPostsAge();

        assertThat(authorIds(items(get("/api/explore?skillId=" + skill, fan)))).doesNotContain(owner.id());
        assertThat(authorIds(items(get("/api/explore", fan)))).doesNotContain(owner.id());

        assertThat(status(call(HttpMethod.PUT, "/api/profile/me", owner, Map.of("isPrivate", false)))).isEqualTo(HttpStatus.OK);

        assertThat(list(get("/api/follow-requests", owner))).isEmpty();
        assertThat(map(get("/api/users/" + owner.id() + "/follow-status", fan)).get("isFollowedByMe")).isEqualTo(true);
        // fan now follows the owner (explore skips followed people), but anybody else can see the post in explore again
        assertThat(authorIds(items(get("/api/explore?skillId=" + skill, register("Newcomer"))))).contains(owner.id());
    }

    @Test
    void privateAccount_publicFollowStillWorksNormally_andNotificationsAreSent() {
        Person pub = register("Pub");
        Person fan = register("Fan");
        assertThat(map(call(HttpMethod.PUT, "/api/users/" + pub.id() + "/follow", fan, null)).get("status")).isEqualTo("ACCEPTED");

        Person owner = register("Owner");
        makePrivate(owner);
        call(HttpMethod.PUT, "/api/users/" + owner.id() + "/follow", fan, null);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipient_id=? AND type='FOLLOW_REQUEST'", Long.class, owner.id())).isEqualTo(1));

        long requestId = id(list(get("/api/follow-requests", owner)).get(0));
        call(HttpMethod.PUT, "/api/follow-requests/" + requestId + "/accept", owner, null);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipient_id=? AND type='FOLLOW_ACCEPTED'", Long.class, fan.id())).isEqualTo(1));
    }

    // ================================================================ reports

    @Test
    void reports_createRejectDuplicatesAndBadInput() {
        Person reporter = register("Reporter");
        Person culprit = register("Culprit");
        long post = post(culprit, "bad post", null);
        long comment = id(map(call(HttpMethod.POST, "/api/posts/" + post + "/comments", culprit, Map.of("content", "bad comment"))));

        Map<String, Object> onUser = Map.of("targetType", "USER", "targetId", culprit.id(), "reason", "SPAM");
        assertThat(status(call(HttpMethod.POST, "/api/reports", reporter, onUser))).isEqualTo(HttpStatus.CREATED);
        assertThat(status(call(HttpMethod.POST, "/api/reports", reporter, onUser))).isEqualTo(HttpStatus.CONFLICT);   // duplicate

        assertThat(status(call(HttpMethod.POST, "/api/reports", reporter, Map.of("targetType", "POST", "targetId", post, "reason", "HARASSMENT")))).isEqualTo(HttpStatus.CREATED);
        assertThat(status(call(HttpMethod.POST, "/api/reports", reporter, Map.of("targetType", "COMMENT", "targetId", comment, "reason", "HATE_SPEECH", "details", "see above")))).isEqualTo(HttpStatus.CREATED);

        assertThat(status(call(HttpMethod.POST, "/api/reports", reporter, Map.of("targetType", "USER", "targetId", reporter.id(), "reason", "SPAM")))).isEqualTo(HttpStatus.BAD_REQUEST);   // myself
        assertThat(status(call(HttpMethod.POST, "/api/reports", culprit, Map.of("targetType", "POST", "targetId", post, "reason", "SPAM")))).isEqualTo(HttpStatus.BAD_REQUEST);   // my own post
        assertThat(status(call(HttpMethod.POST, "/api/reports", reporter, Map.of("targetType", "USER", "targetId", 999999, "reason", "SPAM")))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(call(HttpMethod.POST, "/api/reports", reporter, Map.of("targetType", "POST", "targetId", 999999, "reason", "SPAM")))).isEqualTo(HttpStatus.NOT_FOUND);
        Person other = register("Other");
        assertThat(status(call(HttpMethod.POST, "/api/reports", other, Map.of("targetType", "USER", "targetId", culprit.id(), "reason", "OTHER")))).isEqualTo(HttpStatus.BAD_REQUEST);   // OTHER needs text
        assertThat(status(call(HttpMethod.POST, "/api/reports", other, Map.of("targetType", "USER", "targetId", culprit.id(), "reason", "NOT_A_REASON")))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(status(call(HttpMethod.POST, "/api/reports", other, Map.of("targetType", "USER", "reason", "SPAM")))).isEqualTo(HttpStatus.BAD_REQUEST);   // no target id
        assertThat(status(call(HttpMethod.POST, "/api/reports", null, onUser))).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);

        // reporting someone you just blocked is allowed
        block(other, culprit);
        assertThat(status(call(HttpMethod.POST, "/api/reports", other, Map.of("targetType", "USER", "targetId", culprit.id(), "reason", "SPAM")))).isEqualTo(HttpStatus.CREATED);
    }

    // ================================================================ admin

    @Test
    void admin_endpointsAreForAdminsOnly_resolveWorksOnceAndAdminsCanDeleteAnyPost() {
        Person user = register("User");
        Person culprit = register("Culprit");
        Person admin = admin();
        long post = post(culprit, "to be removed", null);
        call(HttpMethod.POST, "/api/reports", user, Map.of("targetType", "POST", "targetId", post, "reason", "SPAM"));

        // a normal user, and no user at all
        assertThat(status(get("/api/admin/reports", user))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(call(HttpMethod.PUT, "/api/admin/reports/1/resolve", user, null))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(get("/api/admin/reports", null))).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);

        // an admin sees the open reports, with a preview of the target
        List<Map<String, Object>> open = items(get("/api/admin/reports?status=OPEN&size=100", admin));
        Map<String, Object> mine = open.stream().filter(r -> "POST".equals(r.get("targetType")) && ((Number) r.get("targetId")).longValue() == post).findFirst().orElseThrow();
        assertThat(mine.get("targetPreview")).isEqualTo("to be removed");

        long reportId = id(mine);
        assertThat(status(call(HttpMethod.PUT, "/api/admin/reports/" + reportId + "/resolve", admin, Map.of("note", "removed")))).isEqualTo(HttpStatus.OK);
        assertThat(status(call(HttpMethod.PUT, "/api/admin/reports/" + reportId + "/resolve", admin, null))).isEqualTo(HttpStatus.BAD_REQUEST);   // already resolved
        assertThat(status(call(HttpMethod.PUT, "/api/admin/reports/999999/resolve", admin, null))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(items(get("/api/admin/reports?status=OPEN&size=100", admin)).stream().map(this::id)).doesNotContain(reportId);
        assertThat(items(get("/api/admin/reports?status=RESOLVED&size=100", admin)).stream().map(this::id)).contains(reportId);

        // a normal user cannot delete someone else's post, an admin can
        assertThat(status(call(HttpMethod.DELETE, "/api/posts/" + post, user, null))).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        assertThat(status(get("/api/posts/" + post, user))).isEqualTo(HttpStatus.OK);
        assertThat(status(call(HttpMethod.DELETE, "/api/posts/" + post, admin, null)).is2xxSuccessful()).isTrue();
        assertThat(status(get("/api/posts/" + post, user))).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void roleIsReadFromTheDatabaseOnEveryRequest_soDemotionTakesEffectImmediately() {
        Person admin = admin();
        assertThat(status(get("/api/admin/reports", admin))).isEqualTo(HttpStatus.OK);

        jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", admin.id());   // the same token keeps working, but no longer as admin

        assertThat(status(get("/api/admin/reports", admin))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(get("/api/blocks", admin))).isEqualTo(HttpStatus.OK);
    }

    @Test
    void tokenOfADeletedUserIsRejected() {
        Person ghost = register("Ghost");
        jdbc.update("DELETE FROM users WHERE id = ?", ghost.id());

        assertThat(status(get("/api/blocks", ghost))).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
