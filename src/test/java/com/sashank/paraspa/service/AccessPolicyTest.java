package com.sashank.paraspa.service;

import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.FollowStatus;
import com.sashank.paraspa.exception.ForbiddenException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.BlockRepository;
import com.sashank.paraspa.repository.FollowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessPolicyTest {

    @Mock private BlockRepository blockRepository;
    @Mock private FollowRepository followRepository;

    @InjectMocks
    private AccessPolicy policy;

    private final User publicAuthor = User.builder().id(2L).isPrivate(false).build();
    private final User privateAuthor = User.builder().id(3L).isPrivate(true).build();

    // ================================================================ users

    @Test
    void requireUserVisible_aUserBlockedEitherWay_is404() {
        when(blockRepository.existsBetween(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> policy.requireUserVisible(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("User not found");
    }

    @Test
    void requireUserVisible_otherwiseFine_andYouAreAlwaysVisibleToYourself() {
        assertThatCode(() -> policy.requireUserVisible(1L, 2L)).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireUserVisible(1L, 1L)).doesNotThrowAnyException();
        verifyNoInteractions(followRepository);
    }

    // ================================================================ whose posts can I see

    @Test
    void canSeePostsOf_aPublicAccount_yes() {
        assertThat(policy.canSeePostsOf(1L, publicAuthor)).isTrue();
    }

    @Test
    void canSeePostsOf_yourOwnPosts_alwaysYes_evenWhenPrivate() {
        User me = User.builder().id(1L).isPrivate(true).build();

        assertThat(policy.canSeePostsOf(1L, me)).isTrue();
        verifyNoInteractions(blockRepository, followRepository);
    }

    @Test
    void canSeePostsOf_aBlockedAuthor_no_evenIfPublic() {
        when(blockRepository.existsBetween(1L, 2L)).thenReturn(true);

        assertThat(policy.canSeePostsOf(1L, publicAuthor)).isFalse();
    }

    @Test
    void canSeePostsOf_aBlockedAuthor_no_evenIfIWasAnAcceptedFollowerOfAPrivateAccount() {
        when(blockRepository.existsBetween(1L, 3L)).thenReturn(true);

        assertThat(policy.canSeePostsOf(1L, privateAuthor)).isFalse();
        verifyNoInteractions(followRepository);   // the block decides first
    }

    @Test
    void canSeePostsOf_aPrivateAccount_onlyForAcceptedFollowers() {
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(1L, 3L, FollowStatus.ACCEPTED)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(4L, 3L, FollowStatus.ACCEPTED)).thenReturn(false);

        assertThat(policy.canSeePostsOf(1L, privateAuthor)).isTrue();
        assertThat(policy.canSeePostsOf(4L, privateAuthor)).isFalse();   // a stranger; a pending request is not ACCEPTED either
    }

    // ================================================================ lists of a user's posts / followers

    @Test
    void requireCanSeePostsOf_aPrivateAccountYouDoNotFollow_is403_thatSaysWhy() {
        assertThatThrownBy(() -> policy.requireCanSeePostsOf(4L, privateAuthor))
                .isInstanceOf(ForbiddenException.class).hasMessage("This account is private");
    }

    @Test
    void requireCanSeePostsOf_aBlockedUser_is404_notForbidden() {
        when(blockRepository.existsBetween(4L, 3L)).thenReturn(true);

        assertThatThrownBy(() -> policy.requireCanSeePostsOf(4L, privateAuthor))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requireCanSeePostsOf_publicAccountsFollowersAndYourself_pass() {
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(1L, 3L, FollowStatus.ACCEPTED)).thenReturn(true);

        assertThatCode(() -> policy.requireCanSeePostsOf(1L, publicAuthor)).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireCanSeePostsOf(1L, privateAuthor)).doesNotThrowAnyException();   // accepted follower
        assertThatCode(() -> policy.requireCanSeePostsOf(3L, privateAuthor)).doesNotThrowAnyException();   // the owner
    }

    // ================================================================ one post

    @Test
    void requirePostVisible_aHiddenOrPrivatePost_doesNotExist_404() {
        Post privatePost = Post.builder().id(10L).author(privateAuthor).build();

        assertThatThrownBy(() -> policy.requirePostVisible(4L, privatePost))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Post not found");
    }

    @Test
    void requirePostVisible_aPublicPostOfAnUnblockedAuthor_passes() {
        assertThatCode(() -> policy.requirePostVisible(1L, Post.builder().id(10L).author(publicAuthor).build()))
                .doesNotThrowAnyException();
    }
}
