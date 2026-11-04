package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.Block;
import com.sashank.paraspa.entity.Comment;
import com.sashank.paraspa.entity.Follow;
import com.sashank.paraspa.entity.Notification;
import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.entity.PostLike;
import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.FollowStatus;
import com.sashank.paraspa.enums.NotificationTargetType;
import com.sashank.paraspa.enums.NotificationType;
import com.sashank.paraspa.enums.SkillCategory;
import com.sashank.paraspa.enums.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The block and private-account rules, as the database sees them: real queries on an in-memory database, no mocks.
 * Cast: Viewer, plus Ben (blocked by the viewer), Cy (blocked the viewer), Dan (public, fine), Pia (private).
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false"
})
class SafetyQueriesIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private BlockRepository blockRepository;
    @Autowired private FollowRepository followRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;

    private User viewer, ben, cy, dan, pia;
    private Skill skill;

    @BeforeEach
    void cast() {
        viewer = user("viewer", false);
        ben = user("ben", false);
        cy = user("cy", false);
        dan = user("dan", false);
        pia = user("pia", true);
        skill = new Skill();
        skill.setName("Guitar");
        skill.setCategory(SkillCategory.MUSIC);
        em.persist(skill);
        block(viewer, ben);   // the viewer blocked Ben
        block(cy, viewer);    // Cy blocked the viewer
        em.flush();
    }

    // ------------------------------------------------------------------ helpers

    private User user(String name, boolean isPrivate) {
        User u = User.builder().name(name).email(name + "@example.com").password("x").role(UserRole.USER).isPrivate(isPrivate).build();
        em.persist(u);
        return u;
    }

    private void block(User blocker, User blocked) {
        em.persist(Block.builder().blocker(blocker).blocked(blocked).build());
    }

    private Follow follow(User follower, User following, FollowStatus status) {
        Follow f = Follow.builder().follower(follower).following(following).status(status).build();
        em.persist(f);
        return f;
    }

    private Post post(User author, String content) {
        Post p = Post.builder().author(author).content(content).build();
        p.getSkills().add(skill);
        em.persist(p);
        return p;
    }

    private Comment comment(Post post, User author, Comment parent, String text) {
        Comment c = Comment.builder().post(post).author(author).parent(parent).content(text).build();
        em.persist(c);
        return c;
    }

    private static List<String> contents(List<Post> posts) {
        return posts.stream().map(Post::getContent).toList();
    }

    // ================================================================ blocks as seen by queries

    @Test
    void existsBetween_isTrueInBothDirections_andFalseForUnrelatedUsers() {
        assertThat(blockRepository.existsBetween(viewer.getId(), ben.getId())).isTrue();     // I blocked him
        assertThat(blockRepository.existsBetween(ben.getId(), viewer.getId())).isTrue();     // same pair, other way round
        assertThat(blockRepository.existsBetween(viewer.getId(), cy.getId())).isTrue();      // she blocked me
        assertThat(blockRepository.existsBetween(cy.getId(), viewer.getId())).isTrue();
        assertThat(blockRepository.existsBetween(viewer.getId(), dan.getId())).isFalse();
        assertThat(blockRepository.existsBetween(ben.getId(), cy.getId())).isFalse();
    }

    @Test
    void hiddenUserIds_isEveryoneBlockedEitherWay_andNoOneElse() {
        assertThat(blockRepository.findHiddenUserIds(viewer.getId())).containsExactlyInAnyOrder(ben.getId(), cy.getId());
        assertThat(blockRepository.findHiddenUserIds(ben.getId())).containsExactly(viewer.getId());   // the blocked side sees it too
        assertThat(blockRepository.findHiddenUserIds(dan.getId())).isEmpty();
    }

    @Test
    void blockedList_showsOnlyThePeopleIBlocked_notThePeopleWhoBlockedMe() {
        List<Block> mine = blockRepository.findByBlockerIdOrderByIdDesc(viewer.getId(), PageRequest.of(0, 20));

        assertThat(mine).extracting(b -> b.getBlocked().getName()).containsExactly("ben");   // not "cy"
    }

    @Test
    void aBlockCannotBeRecordedTwiceOrAgainstYourself_atTheDatabaseLevel() {
        assertThatThrownBy(() -> {
            em.persist(Block.builder().blocker(viewer).blocked(ben).build());
            em.flush();
        }).isInstanceOf(Exception.class);
    }

    // ================================================================ general post listing (GET /api/posts)

    @Test
    void generalListing_hidesBlockedAuthorsEitherWay_andPrivateAuthorsUntilAccepted() {
        post(viewer, "mine");
        post(ben, "by the user I blocked");
        post(cy, "by the user who blocked me");
        post(dan, "by a public user");
        post(pia, "by a private user");
        em.flush();

        assertThat(contents(postRepository.findVisibleToViewer(viewer.getId(), PageRequest.of(0, 20))))
                .containsExactlyInAnyOrder("mine", "by a public user");
    }

    @Test
    void generalListing_aPendingRequestShowsNothing_anAcceptedFollowShowsThePrivatePosts() {
        post(pia, "private post");
        em.flush();

        Follow request = follow(viewer, pia, FollowStatus.PENDING);
        em.flush();
        assertThat(contents(postRepository.findVisibleToViewer(viewer.getId(), PageRequest.of(0, 20)))).doesNotContain("private post");

        request.setStatus(FollowStatus.ACCEPTED);
        em.flush();
        assertThat(contents(postRepository.findVisibleToViewer(viewer.getId(), PageRequest.of(0, 20)))).contains("private post");
    }

    @Test
    void generalListing_aPrivateAccountsOwnPostsAreAlwaysVisibleToIt() {
        post(pia, "pia's own");
        em.flush();

        assertThat(contents(postRepository.findVisibleToViewer(pia.getId(), PageRequest.of(0, 20)))).contains("pia's own");
    }

    @Test
    void generalListing_bySkill_appliesTheSameRules() {
        post(ben, "ben guitar");
        post(dan, "dan guitar");
        post(pia, "pia guitar");
        em.flush();

        assertThat(contents(postRepository.findVisibleToViewerBySkill(viewer.getId(), skill.getId(), PageRequest.of(0, 20))))
                .containsExactly("dan guitar");
    }

    @Test
    void generalListing_aFollowThatBecameAcceptedThenWasRemovedByABlock_showsNothingAgain() {
        post(pia, "private post");
        follow(viewer, pia, FollowStatus.ACCEPTED);
        em.flush();
        assertThat(contents(postRepository.findVisibleToViewer(viewer.getId(), PageRequest.of(0, 20)))).contains("private post");

        block(pia, viewer);   // the private account blocks the viewer: the accepted follow no longer helps
        em.flush();

        assertThat(contents(postRepository.findVisibleToViewer(viewer.getId(), PageRequest.of(0, 20)))).doesNotContain("private post");
    }

    // ================================================================ comments

    @Test
    void comments_fromUsersBlockedEitherWayAreLeftOut_ofTopLevelAndReplies() {
        Post p = post(dan, "dan's post");
        Comment fine = comment(p, dan, null, "fine");
        comment(p, ben, null, "by the user I blocked");
        comment(p, cy, null, "by the user who blocked me");
        comment(p, dan, fine, "reply by dan");
        comment(p, ben, fine, "reply by the blocked user");
        comment(p, cy, fine, "reply by the user who blocked me");
        em.flush();

        List<Comment> top = commentRepository.findTopLevelVisible(p.getId(), viewer.getId(), PageRequest.of(0, 20));
        List<Comment> replies = commentRepository.findRepliesVisible(List.of(fine.getId()), viewer.getId());

        assertThat(top).extracting(Comment::getContent).containsExactly("fine");
        assertThat(replies).extracting(Comment::getContent).containsExactly("reply by dan");
    }

    @Test
    void comments_theSameCommentsAreStillVisibleToEveryoneWhoIsNotBlocked() {
        Post p = post(dan, "dan's post");
        comment(p, ben, null, "by ben");
        comment(p, dan, null, "by dan");
        em.flush();

        // Ben only blocked (and is blocked by) the viewer; to Dan nothing is hidden
        assertThat(commentRepository.findTopLevelVisible(p.getId(), dan.getId(), PageRequest.of(0, 20)))
                .extracting(Comment::getContent).containsExactly("by ben", "by dan");
    }

    @Test
    void comments_aBlockedUsersCommentsAreHiddenFromTheBlockedUserToo_symmetric() {
        Post p = post(dan, "dan's post");
        comment(p, viewer, null, "by the viewer");
        comment(p, dan, null, "by dan");
        em.flush();

        // Ben is blocked by the viewer, so Ben cannot see the viewer's comment either
        assertThat(commentRepository.findTopLevelVisible(p.getId(), ben.getId(), PageRequest.of(0, 20)))
                .extracting(Comment::getContent).containsExactly("by dan");
    }

    @Test
    void comments_paging_countsOnlyVisibleComments_soAPageIsNeverShortBecauseOfHiddenOnes() {
        Post p = post(dan, "dan's post");
        comment(p, dan, null, "1");
        comment(p, ben, null, "hidden a");
        comment(p, ben, null, "hidden b");
        comment(p, dan, null, "2");
        comment(p, cy, null, "hidden c");
        comment(p, dan, null, "3");
        em.flush();

        List<Comment> firstPage = commentRepository.findTopLevelVisible(p.getId(), viewer.getId(), PageRequest.of(0, 2));
        List<Comment> afterFirst = commentRepository.findTopLevelVisibleAfter(p.getId(), firstPage.get(1).getId(), viewer.getId(), PageRequest.of(0, 2));

        assertThat(firstPage).extracting(Comment::getContent).containsExactly("1", "2");   // the filter is in the query, not applied afterwards
        assertThat(afterFirst).extracting(Comment::getContent).containsExactly("3");
    }

    // ================================================================ likes list

    @Test
    void likers_blockedEitherWayAreLeftOut() {
        Post p = post(dan, "dan's post");
        for (User u : List.of(viewer, ben, cy, dan, pia)) {
            em.persist(PostLike.builder().post(p).user(u).build());
        }
        em.flush();

        assertThat(postLikeRepository.findVisibleLikes(p.getId(), viewer.getId(), PageRequest.of(0, 20)))
                .extracting(l -> l.getUser().getName()).containsExactlyInAnyOrder("viewer", "dan", "pia");
    }

    // ================================================================ followers / following lists

    @Test
    void followerLists_showOnlyAcceptedFollows_andHideBlockedUsers() {
        follow(dan, pia, FollowStatus.ACCEPTED);
        follow(ben, pia, FollowStatus.ACCEPTED);                  // blocked by the viewer
        follow(cy, pia, FollowStatus.ACCEPTED);                   // blocked the viewer
        follow(viewer, pia, FollowStatus.PENDING);                // still waiting: not a follower
        follow(pia, dan, FollowStatus.ACCEPTED);
        follow(pia, ben, FollowStatus.ACCEPTED);
        em.flush();

        assertThat(followRepository.findAcceptedFollowers(pia.getId(), viewer.getId(), PageRequest.of(0, 20)))
                .extracting(f -> f.getFollower().getName()).containsExactly("dan");
        assertThat(followRepository.findAcceptedFollowing(pia.getId(), viewer.getId(), PageRequest.of(0, 20)))
                .extracting(f -> f.getFollowing().getName()).containsExactly("dan");
    }

    @Test
    void followCountsAndIds_countAcceptedFollowsOnly() {
        follow(dan, pia, FollowStatus.ACCEPTED);
        follow(ben, pia, FollowStatus.PENDING);
        follow(pia, dan, FollowStatus.ACCEPTED);
        follow(pia, cy, FollowStatus.PENDING);
        em.flush();

        assertThat(followRepository.countByFollowingIdAndStatus(pia.getId(), FollowStatus.ACCEPTED)).isEqualTo(1);
        assertThat(followRepository.countByFollowerIdAndStatus(pia.getId(), FollowStatus.ACCEPTED)).isEqualTo(1);
        assertThat(followRepository.findAcceptedFollowingIds(pia.getId())).containsExactly(dan.getId());
        assertThat(followRepository.findFollowedOrRequestedIds(pia.getId())).containsExactlyInAnyOrder(dan.getId(), cy.getId());
    }

    // ================================================================ follow requests

    @Test
    void requests_listsOnlyPendingOnesAddressedToTheOwner() {
        Follow toPia = follow(dan, pia, FollowStatus.PENDING);
        follow(ben, pia, FollowStatus.ACCEPTED);
        follow(dan, cy, FollowStatus.PENDING);   // addressed to somebody else
        em.flush();

        assertThat(followRepository.findByFollowingIdAndStatusOrderByCreatedAtDescIdDesc(pia.getId(), FollowStatus.PENDING, PageRequest.of(0, 20)))
                .extracting(Follow::getId).containsExactly(toPia.getId());
        assertThat(followRepository.findByIdAndFollowingIdAndStatus(toPia.getId(), pia.getId(), FollowStatus.PENDING)).isPresent();
        assertThat(followRepository.findByIdAndFollowingIdAndStatus(toPia.getId(), cy.getId(), FollowStatus.PENDING)).isEmpty();   // not mine
    }

    @Test
    void acceptAllPending_approvesOnlyThatAccountsWaitingRequests() {
        Follow waiting = follow(dan, pia, FollowStatus.PENDING);
        Follow otherAccountsRequest = follow(dan, cy, FollowStatus.PENDING);
        em.flush();

        int changed = followRepository.acceptAllPending(pia.getId());
        em.clear();

        assertThat(changed).isEqualTo(1);
        assertThat(followRepository.findById(waiting.getId()).orElseThrow().getStatus()).isEqualTo(FollowStatus.ACCEPTED);
        assertThat(followRepository.findById(otherAccountsRequest.getId()).orElseThrow().getStatus()).isEqualTo(FollowStatus.PENDING);
    }

    // ================================================================ cleanup done by a block

    @Test
    void deleteBetween_removesFollowsAndRequestsInBothDirections_andNothingElse() {
        follow(viewer, dan, FollowStatus.ACCEPTED);
        follow(dan, viewer, FollowStatus.PENDING);     // a request the other way, still removed
        Follow unrelated = follow(dan, pia, FollowStatus.ACCEPTED);
        Follow unrelated2 = follow(viewer, pia, FollowStatus.ACCEPTED);
        em.flush();

        int removed = followRepository.deleteBetween(viewer.getId(), dan.getId());
        em.clear();

        assertThat(removed).isEqualTo(2);
        assertThat(followRepository.findByFollowerIdAndFollowingId(viewer.getId(), dan.getId())).isEmpty();
        assertThat(followRepository.findByFollowerIdAndFollowingId(dan.getId(), viewer.getId())).isEmpty();
        assertThat(followRepository.findById(unrelated.getId())).isPresent();
        assertThat(followRepository.findById(unrelated2.getId())).isPresent();
    }

    @Test
    void notificationsBetweenTwoUsers_areDeletedInBothDirections_othersAreKept() {
        notification(dan, viewer);
        notification(viewer, dan);
        notification(dan, pia);          // unrelated
        em.flush();

        int removed = notificationRepository.deleteBetween(viewer.getId(), dan.getId());

        assertThat(removed).isEqualTo(2);
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    private void notification(User recipient, User actor) {
        em.persist(Notification.builder().recipient(recipient).actor(actor).type(NotificationType.COMMENT)
                .targetType(NotificationTargetType.POST).targetId(1L).createdAt(LocalDateTime.now()).build());
    }

    // ================================================================ roles and defaults

    @Test
    void roleLookup_returnsTheCurrentRole_andNothingForAMissingUser() {
        User admin = User.builder().name("root").email("root@example.com").password("x").role(UserRole.ADMIN).build();
        em.persist(admin);
        em.flush();

        assertThat(userRepository.findRoleById(admin.getId())).contains(UserRole.ADMIN);
        assertThat(userRepository.findRoleById(dan.getId())).contains(UserRole.USER);
        assertThat(userRepository.findRoleById(999_999L)).isEmpty();
    }

    @Test
    void newAccountsArePublic_andNewFollowsAreAcceptedByDefault() {
        User fresh = User.builder().name("fresh").email("fresh@example.com").password("x").build();
        Follow plain = Follow.builder().follower(fresh).following(dan).build();

        assertThat(fresh.isPrivate()).isFalse();
        assertThat(plain.getStatus()).isEqualTo(FollowStatus.ACCEPTED);
    }
}
