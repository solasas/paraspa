package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.repository.PostLikeRepository;
import com.sashank.paraspa.repository.PostRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.PostService;
import com.sashank.paraspa.support.PostgresSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "One like per user per post" on real PostgreSQL: the unique constraint, the real {@code ON CONFLICT DO NOTHING}
 * insert, and many simultaneous requests. (H2 has no {@code ON CONFLICT}, so this cannot be proven there.)
 */
@EnabledIf("com.sashank.paraspa.support.PostgresSupport#available")
@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.com.sashank.paraspa=WARN"})
class PostgresLikeUniquenessTest {

    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        PostgresSupport.register(registry);
    }

    @Autowired private PostService postService;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private User user(String name) {
        return userRepository.save(User.builder().name(name).email(name + UNIQUE.incrementAndGet() + System.nanoTime() + "@example.com").password("x").build());
    }

    private Post post(User author) {
        return postRepository.save(Post.builder().author(author).content("hello").build());
    }

    private long likeRows(Post post) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM post_likes WHERE post_id = ?", Long.class, post.getId());
    }

    private long likeCounter(Post post) {
        return jdbc.queryForObject("SELECT like_count FROM posts WHERE id = ?", Long.class, post.getId());
    }

    @Test
    void theRealInsert_addsOnce_thenReportsZeroRowsForTheRepeat() {
        User author = user("author"), fan = user("fan");
        Post post = post(author);

        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        int first = tx.execute(s -> postLikeRepository.insertIfAbsent(post.getId(), fan.getId(), LocalDateTime.now()));
        int second = tx.execute(s -> postLikeRepository.insertIfAbsent(post.getId(), fan.getId(), LocalDateTime.now()));

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(likeRows(post)).isEqualTo(1);
    }

    @Test
    void theDatabaseItselfRefusesADuplicateLike() {
        User author = user("author"), fan = user("fan");
        Post post = post(author);
        jdbc.update("INSERT INTO post_likes (post_id, user_id, created_at) VALUES (?, ?, ?)", post.getId(), fan.getId(), Timestamp.valueOf(LocalDateTime.now()));

        assertThatThrownBy(() -> jdbc.update("INSERT INTO post_likes (post_id, user_id, created_at) VALUES (?, ?, ?)",
                post.getId(), fan.getId(), Timestamp.valueOf(LocalDateTime.now())))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void twentySimultaneousLikesFromTheSameUser_leaveExactlyOneLike_andACounterOfOne() throws Exception {
        User author = user("author"), fan = user("fan");
        Post post = post(author);

        runConcurrently(20, i -> () -> {
            postService.likePost(fan.getId(), post.getId());
            return null;
        });

        assertThat(likeRows(post)).isEqualTo(1);
        assertThat(likeCounter(post)).isEqualTo(1);
    }

    @Test
    void simultaneousLikesFromDifferentUsers_areAllCounted_withoutLostUpdates() throws Exception {
        User author = user("author");
        Post post = post(author);
        List<User> fans = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            fans.add(user("fan"));
        }

        runConcurrently(15, i -> () -> {
            postService.likePost(fans.get(i).getId(), post.getId());
            return null;
        });

        assertThat(likeRows(post)).isEqualTo(15);
        assertThat(likeCounter(post)).isEqualTo(15);
    }

    @Test
    void likeUnlikeLike_keepsTheCounterEqualToTheRows() {
        User author = user("author"), fan = user("fan");
        Post post = post(author);

        postService.likePost(fan.getId(), post.getId());
        postService.likePost(fan.getId(), post.getId());      // repeat: no-op
        postService.unlikePost(fan.getId(), post.getId());
        postService.unlikePost(fan.getId(), post.getId());    // repeat: no-op, counter must not go negative
        assertThat(likeRows(post)).isZero();
        assertThat(likeCounter(post)).isZero();

        postService.likePost(fan.getId(), post.getId());
        assertThat(likeRows(post)).isEqualTo(1);
        assertThat(likeCounter(post)).isEqualTo(1);
    }

    private interface Task {
        Callable<Object> forIndex(int i);
    }

    private void runConcurrently(int threads, Task task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Object>> calls = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                calls.add(task.forIndex(i));
            }
            for (Future<Object> f : pool.invokeAll(calls)) {
                f.get();   // rethrows anything a request threw: none may fail
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
