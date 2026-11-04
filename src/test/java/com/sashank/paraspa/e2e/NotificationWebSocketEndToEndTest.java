package com.sashank.paraspa.e2e;

import com.sashank.paraspa.dto.response.AuthResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.sashank.paraspa.repository.PostLikeRepository;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * The whole path, for real: HTTP requests go through the controllers and services, the domain events fire after
 * commit, the notification is stored, and it arrives on a real STOMP-over-WebSocket subscription to
 * {@code /user/queue/notifications} that was opened with a real JWT. Runs on an in-memory H2 database (PostgreSQL mode).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:notifications_e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class NotificationWebSocketEndToEndTest {

    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate rest;
    @Autowired private SimpUserRegistry userRegistry;
    @Autowired private JdbcTemplate jdbc;

    /**
     * H2 does not understand PostgreSQL's {@code INSERT ... ON CONFLICT DO NOTHING}, which the real like query uses
     * (its concurrent behaviour is covered against PostgreSQL elsewhere). Only that one repository method is replaced here by an
     * equivalent portable statement, so everything else on the like path (controller, service, event, listener,
     * notification, WebSocket push) is the real code.
     */
    @SpyBean private PostLikeRepository postLikeRepository;

    private WebSocketStompClient stompClient;
    private final List<StompSession> sessions = new ArrayList<>();

    private record Person(long id, String name, String email, String token) {
    }

    @BeforeEach
    void startClient() {
        doAnswer(invocation -> jdbc.update(
                "INSERT INTO post_likes (post_id, user_id, created_at) SELECT ?, ?, ? WHERE NOT EXISTS "
                        + "(SELECT 1 FROM post_likes WHERE post_id = ? AND user_id = ?)",
                invocation.getArgument(0), invocation.getArgument(1), java.sql.Timestamp.valueOf((java.time.LocalDateTime) invocation.getArgument(2)),
                invocation.getArgument(0), invocation.getArgument(1)))
                .when(postLikeRepository).insertIfAbsent(any(), any(), any());
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    void closeSessions() {
        sessions.forEach(s -> {
            if (s.isConnected()) {
                s.disconnect();
            }
        });
        stompClient.stop();
    }

    // ------------------------------------------------------------------ helpers

    private Person register(String name) {
        String email = name.toLowerCase() + UNIQUE.incrementAndGet() + "@example.com";
        ResponseEntity<AuthResponse> response = rest.postForEntity("/api/auth/register",
                Map.of("name", name, "email", email, "password", "secret123"), AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new Person(response.getBody().getId(), name, email, response.getBody().getToken());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResponseEntity<Map> call(HttpMethod method, String path, Person as, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(as.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<String> callForText(HttpMethod method, String path, Person as) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(as.token());
        return rest.exchange(path, method, new HttpEntity<>(null, headers), String.class);
    }

    /** Notifications are created a moment after the request (on their own executor), so wait for them. */
    private void awaitUnread(Person p, long expected) {
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(unreadCount(p)).isEqualTo(expected));
    }

    private long unreadCount(Person p) {
        return Long.parseLong(callForText(HttpMethod.GET, "/api/notifications/unread-count", p).getBody());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> notificationsOf(Person p) {
        return (List<Map<String, Object>>) call(HttpMethod.GET, "/api/notifications?size=100", p, null).getBody().get("items");
    }

    private long createPost(Person author, String content) {
        ResponseEntity<Map> r = call(HttpMethod.POST, "/api/posts", author, Map.of("content", content));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) r.getBody().get("id")).longValue();
    }

    private long comment(Person who, long postId, String text, Long parentId) {
        Map<String, Object> body = parentId == null ? Map.of("content", text) : Map.of("content", text, "parentId", parentId);
        ResponseEntity<Map> r = call(HttpMethod.POST, "/api/posts/" + postId + "/comments", who, body);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) r.getBody().get("id")).longValue();
    }

    /** Opens a WebSocket as this person, subscribes to /user/queue/notifications and waits until the subscription is active. */
    private BlockingQueue<Map<String, Object>> listen(Person who) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + who.token());
        StompSession session = stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                connectHeaders, new StompSessionHandlerAdapter() { }).get(10, TimeUnit.SECONDS);
        sessions.add(session);

        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/notifications", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });
        awaitSubscription(who);
        return received;
    }

    /** Waits until the broker has registered this person's subscription, so nothing pushed afterwards can be missed. */
    private void awaitSubscription(Person who) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            SimpUser user = userRegistry.getUser(who.email());
            if (user != null && user.getSessions().stream().flatMap(sess -> sess.getSubscriptions().stream())
                    .map(SimpSubscription::getDestination).anyMatch(d -> d.endsWith("/queue/notifications"))) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("the subscription of " + who.email() + " was never registered");
    }

    private Map<String, Object> next(BlockingQueue<Map<String, Object>> queue) throws InterruptedException {
        Map<String, Object> message = queue.poll(5, TimeUnit.SECONDS);
        assertThat(message).as("a notification should have been pushed").isNotNull();
        return message;
    }

    private void assertNothing(BlockingQueue<Map<String, Object>> queue) throws InterruptedException {
        assertThat(queue.poll(700, TimeUnit.MILLISECONDS)).as("nothing should have been pushed").isNull();
    }

    private static Map<?, ?> actorOf(Map<String, Object> notification) {
        return (Map<?, ?>) notification.get("actor");
    }

    // ============================================================ the push itself

    @Test
    void following_pushesAFollowNotificationToTheFollowedPersonOverTheirWebSocket() throws Exception {
        Person asha = register("Asha");
        Person ben = register("Ben");
        BlockingQueue<Map<String, Object>> ashasQueue = listen(asha);

        ResponseEntity<Map> follow = call(HttpMethod.PUT, "/api/users/" + asha.id() + "/follow", ben, null);
        assertThat(follow.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> pushed = next(ashasQueue);
        assertThat(pushed.get("type")).isEqualTo("FOLLOW");
        assertThat(pushed.get("targetType")).isEqualTo("USER");
        assertThat(((Number) pushed.get("targetId")).longValue()).isEqualTo(ben.id());   // opens the follower's profile
        assertThat(pushed.get("read")).isEqualTo(false);
        assertThat(((Number) pushed.get("id")).longValue()).isPositive();
        assertThat(actorOf(pushed).get("name")).isEqualTo("Ben");
        assertThat(actorOf(pushed).get("email")).isNull();                       // public fields only
        // the same shape and date format as the REST list: an ISO string, not an array of numbers
        assertThat(pushed.get("createdAt")).isInstanceOf(String.class);

        // the pushed notification is the stored one
        List<Map<String, Object>> listed = notificationsOf(asha);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).get("id")).isEqualTo(pushed.get("id"));
        assertThat(unreadCount(asha)).isEqualTo(1);
    }

    @Test
    void aNotificationReachesOnlyItsRecipient() throws Exception {
        Person asha = register("Asha");
        Person ben = register("Ben");
        Person cy = register("Cy");
        BlockingQueue<Map<String, Object>> ashasQueue = listen(asha);
        BlockingQueue<Map<String, Object>> bensQueue = listen(ben);
        BlockingQueue<Map<String, Object>> cysQueue = listen(cy);

        call(HttpMethod.PUT, "/api/users/" + asha.id() + "/follow", ben, null);

        next(ashasQueue);
        assertNothing(bensQueue);     // the actor is not told about their own action
        assertNothing(cysQueue);      // and nobody else sees it
    }

    @Test
    void aPersonWhoIsNotConnected_findsTheNotificationInTheListInstead() {
        Person asha = register("Asha");
        Person ben = register("Ben");

        call(HttpMethod.PUT, "/api/users/" + asha.id() + "/follow", ben, null);

        awaitUnread(asha, 1);
        assertThat(notificationsOf(asha)).hasSize(1);
    }

    @Test
    void theWebSocketRefusesAConnectionWithoutAValidToken() {
        StompHeaders noToken = new StompHeaders();

        assertThatThrownBy(() -> stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                noToken, new StompSessionHandlerAdapter() { }).get(10, TimeUnit.SECONDS))
                .isInstanceOfAny(ExecutionException.class, java.util.concurrent.TimeoutException.class);
    }

    // ============================================================ what notifies whom

    @Test
    void likingAPost_notifiesTheAuthorOnce_evenIfTheLikeIsRemovedAndAddedAgain() throws Exception {
        Person asha = register("Asha");
        Person ben = register("Ben");
        long postId = createPost(asha, "my post");
        BlockingQueue<Map<String, Object>> ashasQueue = listen(asha);

        assertThat(call(HttpMethod.PUT, "/api/posts/" + postId + "/like", ben, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> like = next(ashasQueue);
        assertThat(like.get("type")).isEqualTo("LIKE");
        assertThat(like.get("targetType")).isEqualTo("POST");
        assertThat(((Number) like.get("targetId")).longValue()).isEqualTo(postId);
        assertThat(actorOf(like).get("name")).isEqualTo("Ben");

        call(HttpMethod.PUT, "/api/posts/" + postId + "/like", ben, null);          // repeated PUT: not even a new like
        call(HttpMethod.DELETE, "/api/posts/" + postId + "/like", ben, null);        // unlike
        call(HttpMethod.PUT, "/api/posts/" + postId + "/like", ben, null);          // like again
        assertNothing(ashasQueue);
        assertThat(notificationsOf(asha)).hasSize(1);
        assertThat(unreadCount(asha)).isEqualTo(1);
    }

    @Test
    void nobodyIsNotifiedAboutTheirOwnLikesCommentsAndReplies() throws Exception {
        Person asha = register("Asha");
        long postId = createPost(asha, "my post");
        BlockingQueue<Map<String, Object>> ashasQueue = listen(asha);

        call(HttpMethod.PUT, "/api/posts/" + postId + "/like", asha, null);
        long own = comment(asha, postId, "a comment on my own post", null);
        comment(asha, postId, "replying to myself", own);
        assertThat(call(HttpMethod.PUT, "/api/users/" + asha.id() + "/follow", asha, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);                                 // you cannot follow yourself

        assertNothing(ashasQueue);
        assertThat(notificationsOf(asha)).isEmpty();
        assertThat(unreadCount(asha)).isZero();
    }

    @Test
    void aCommentNotifiesThePostAuthor_andAReplyNotifiesTheAuthorOfTheCommentRepliedTo() throws Exception {
        Person asha = register("Asha");   // writes the post
        Person ben = register("Ben");     // comments
        Person cy = register("Cy");       // replies to Ben
        long postId = createPost(asha, "my post");
        BlockingQueue<Map<String, Object>> ashasQueue = listen(asha);
        BlockingQueue<Map<String, Object>> bensQueue = listen(ben);

        long bensComment = comment(ben, postId, "nice post", null);
        Map<String, Object> commentNotification = next(ashasQueue);
        assertThat(commentNotification.get("type")).isEqualTo("COMMENT");
        assertThat(((Number) commentNotification.get("targetId")).longValue()).isEqualTo(postId);
        assertNothing(bensQueue);

        comment(cy, postId, "agreed, Ben", bensComment);
        Map<String, Object> replyNotification = next(bensQueue);
        assertThat(replyNotification.get("type")).isEqualTo("REPLY");
        assertThat(actorOf(replyNotification).get("name")).isEqualTo("Cy");
        assertNothing(ashasQueue);        // the post author is not told about replies between other people
    }

    @Test
    void anExchangeRequestNotifiesTheReceiver_andAcceptingNotifiesTheRequester() throws Exception {
        Person asha = register("Asha");
        Person ben = register("Ben");
        // Ben teaches skill 1 (offered), Asha teaches skill 2 (wanted); skills are seeded at startup
        call(HttpMethod.POST, "/api/profile/skills", ben, Map.of("skillId", 1, "type", "TEACH", "level", "BEGINNER"));
        call(HttpMethod.POST, "/api/profile/skills", asha, Map.of("skillId", 2, "type", "TEACH", "level", "BEGINNER"));
        BlockingQueue<Map<String, Object>> ashasQueue = listen(asha);
        BlockingQueue<Map<String, Object>> bensQueue = listen(ben);

        ResponseEntity<Map> created = call(HttpMethod.POST, "/api/exchanges", ben,
                Map.of("receiverId", asha.id(), "offeredSkillId", 1, "wantedSkillId", 2, "message", "swap?"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long exchangeId = ((Number) created.getBody().get("id")).longValue();

        Map<String, Object> request = next(ashasQueue);
        assertThat(request.get("type")).isEqualTo("EXCHANGE_REQUEST");
        assertThat(request.get("targetType")).isEqualTo("EXCHANGE");
        assertThat(((Number) request.get("targetId")).longValue()).isEqualTo(exchangeId);
        assertThat(actorOf(request).get("name")).isEqualTo("Ben");
        assertNothing(bensQueue);

        assertThat(call(HttpMethod.PUT, "/api/exchanges/" + exchangeId + "/accept", asha, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> accepted = next(bensQueue);
        assertThat(accepted.get("type")).isEqualTo("EXCHANGE_ACCEPTED");
        assertThat(((Number) accepted.get("targetId")).longValue()).isEqualTo(exchangeId);
        assertThat(actorOf(accepted).get("name")).isEqualTo("Asha");
        assertNothing(ashasQueue);
    }

    @Test
    void aRejectedAction_createsNoNotification() throws Exception {
        Person asha = register("Asha");
        Person ben = register("Ben");
        Person cy = register("Cy");
        long postId = createPost(asha, "my post");
        long topLevel = comment(ben, postId, "top", null);
        BlockingQueue<Map<String, Object>> bensQueue = listen(ben);
        long reply = comment(cy, postId, "reply", topLevel);    // Ben is told about this one ...
        assertThat(next(bensQueue).get("type")).isEqualTo("REPLY");   // ... and we drain it so only new pushes count below

        // replying to a reply is refused (400) ...
        assertThat(call(HttpMethod.POST, "/api/posts/" + postId + "/comments", asha,
                Map.of("content", "too deep", "parentId", reply)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // ... and so is liking a post that does not exist (404)
        assertThat(call(HttpMethod.PUT, "/api/posts/999999/like", asha, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertNothing(bensQueue);
        assertThat(unreadCount(ben)).isEqualTo(1);     // still only the one real reply
    }

    // ============================================================ messages

    @Test
    void manyMessagesFromOnePerson_pushEachTime_butKeepOneUnreadNotificationPointingAtTheNewest() throws Exception {
        Person asha = register("Asha");
        Person ben = register("Ben");
        BlockingQueue<Map<String, Object>> ashasQueue = listen(asha);

        List<Long> messageIds = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            ResponseEntity<Map> sent = call(HttpMethod.POST, "/api/messages", ben, Map.of("receiverId", asha.id(), "content", "hi " + i));
            assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            messageIds.add(((Number) sent.getBody().get("id")).longValue());
        }

        Map<String, Object> first = next(ashasQueue);
        Map<String, Object> second = next(ashasQueue);
        Map<String, Object> third = next(ashasQueue);
        assertThat(first.get("type")).isEqualTo("MESSAGE");
        assertThat(first.get("targetType")).isEqualTo("MESSAGE");
        // one notification, updated: same id every time, pointing at the newest message
        assertThat(second.get("id")).isEqualTo(first.get("id"));
        assertThat(third.get("id")).isEqualTo(first.get("id"));
        assertThat(((Number) third.get("targetId")).longValue()).isEqualTo(messageIds.get(2));

        assertThat(notificationsOf(asha)).hasSize(1);
        assertThat(unreadCount(asha)).isEqualTo(1);

        // once Asha has seen it, the next message from Ben is news again
        assertThat(call(HttpMethod.PUT, "/api/notifications/" + first.get("id") + "/read", asha, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        call(HttpMethod.POST, "/api/messages", ben, Map.of("receiverId", asha.id(), "content", "hello again"));
        Map<String, Object> fresh = next(ashasQueue);
        assertThat(fresh.get("id")).isNotEqualTo(first.get("id"));
        assertThat(notificationsOf(asha)).hasSize(2);
        assertThat(unreadCount(asha)).isEqualTo(1);
    }

    // ============================================================ read / unread over HTTP

    @Test
    void readAndUnread_workThroughTheRestApi() throws Exception {
        Person asha = register("Asha");
        Person ben = register("Ben");
        Person cy = register("Cy");
        long postId = createPost(asha, "my post");
        BlockingQueue<Map<String, Object>> ashasQueue = listen(asha);

        call(HttpMethod.PUT, "/api/users/" + asha.id() + "/follow", ben, null);
        call(HttpMethod.PUT, "/api/posts/" + postId + "/like", ben, null);
        comment(cy, postId, "hello", null);
        next(ashasQueue);
        next(ashasQueue);
        next(ashasQueue);

        assertThat(unreadCount(asha)).isEqualTo(3);
        List<Map<String, Object>> list = notificationsOf(asha);
        assertThat(list).hasSize(3).allSatisfy(n -> assertThat(n.get("read")).isEqualTo(false));
        assertThat(list.stream().map(n -> n.get("type")).toList()).containsExactly("COMMENT", "LIKE", "FOLLOW");   // newest first

        long newest = ((Number) list.get(0).get("id")).longValue();
        assertThat(callForText(HttpMethod.PUT, "/api/notifications/" + newest + "/read", asha).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(callForText(HttpMethod.PUT, "/api/notifications/" + newest + "/read", asha).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);   // again: fine
        assertThat(unreadCount(asha)).isEqualTo(2);
        assertThat(notificationsOf(asha).get(0).get("read")).isEqualTo(true);

        // somebody else's notification, and one that does not exist: both 404, nothing changes
        assertThat(callForText(HttpMethod.PUT, "/api/notifications/" + newest + "/read", cy).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(callForText(HttpMethod.PUT, "/api/notifications/999999/read", asha).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unreadCount(asha)).isEqualTo(2);

        // Cy marking everything read must not touch Asha's
        assertThat(callForText(HttpMethod.PUT, "/api/notifications/read-all", cy).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(unreadCount(asha)).isEqualTo(2);

        assertThat(callForText(HttpMethod.PUT, "/api/notifications/read-all", asha).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(unreadCount(asha)).isZero();
        assertThat(notificationsOf(asha)).allSatisfy(n -> assertThat(n.get("read")).isEqualTo(true));
    }

    @Test
    void theListIsCursorPaginated_newestFirst() throws Exception {
        Person asha = register("Asha");
        long postId = createPost(asha, "my post");
        for (int i = 0; i < 5; i++) {
            comment(register("Fan" + i), postId, "comment " + i, null);
        }
        awaitUnread(asha, 5);

        ResponseEntity<Map> firstPage = call(HttpMethod.GET, "/api/notifications?size=2", asha, null);
        assertThat((List<?>) firstPage.getBody().get("items")).hasSize(2);
        String cursor = (String) firstPage.getBody().get("nextCursor");
        assertThat(cursor).isNotNull();

        List<Object> ids = new ArrayList<>();
        ((List<Map<String, Object>>) firstPage.getBody().get("items")).forEach(n -> ids.add(n.get("id")));
        while (cursor != null) {
            ResponseEntity<Map> page = call(HttpMethod.GET, "/api/notifications?size=2&cursor=" + cursor, asha, null);
            ((List<Map<String, Object>>) page.getBody().get("items")).forEach(n -> ids.add(n.get("id")));
            cursor = (String) page.getBody().get("nextCursor");
        }
        assertThat(ids).hasSize(5).doesNotHaveDuplicates();
        List<Long> asNumbers = ids.stream().map(i -> ((Number) i).longValue()).toList();
        assertThat(asNumbers).isSortedAccordingTo(java.util.Comparator.reverseOrder());   // newest (highest id) first

        assertThat(call(HttpMethod.GET, "/api/notifications?cursor=garbage", asha, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.getForEntity("/api/notifications", String.class).getStatusCode().value()).isIn(401, 403);   // needs a token
    }
}
