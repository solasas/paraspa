package com.sashank.paraspa.service;

import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.FollowStatus;
import com.sashank.paraspa.exception.ForbiddenException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.BlockRepository;
import com.sashank.paraspa.repository.FollowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The two rules that decide whether someone may see another user's content, in one place:
 * <ul>
 *   <li><b>Blocks</b>, in both directions: nothing of a blocked pair is visible to either side.</li>
 *   <li><b>Private accounts</b>: their posts are visible only to the owner and to ACCEPTED followers (a pending
 *       follow request does not count).</li>
 * </ul>
 * Listings apply the same rules inside their queries (see {@code QueryFragments}); this class is for the single-item
 * and per-user checks.
 */
@Component
public class AccessPolicy {

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private FollowRepository followRepository;

    /** A user blocked either way with the viewer does not exist, as far as the viewer can tell: 404. */
    public void requireUserVisible(Long viewerId, Long targetUserId) {
        if (!viewerId.equals(targetUserId) && blockRepository.existsBetween(viewerId, targetUserId)) {
            throw new ResourceNotFoundException("User not found");
        }
    }

    /** True when the viewer may see the posts of this author (not blocked; public, or self, or accepted follower). */
    public boolean canSeePostsOf(Long viewerId, User author) {
        if (viewerId.equals(author.getId())) {
            return true;
        }
        if (blockRepository.existsBetween(viewerId, author.getId())) {
            return false;
        }
        return !author.isPrivate() || isAcceptedFollower(viewerId, author.getId());
    }

    /**
     * For a user's post list or follower lists: blocked either way is a 404 (as if the user did not exist); a private
     * account the viewer does not follow is a 403 "This account is private". The profile itself stays visible, with its
     * {@code isPrivate} flag, so the client knows why.
     */
    public void requireCanSeePostsOf(Long viewerId, User author) {
        requireUserVisible(viewerId, author.getId());
        if (!viewerId.equals(author.getId()) && author.isPrivate() && !isAcceptedFollower(viewerId, author.getId())) {
            throw new ForbiddenException("This account is private");
        }
    }

    /** For one post (view, like, comment, list likes or comments): hidden or private posts do not exist: 404. */
    public void requirePostVisible(Long viewerId, Post post) {
        if (!canSeePostsOf(viewerId, post.getAuthor())) {
            throw new ResourceNotFoundException("Post not found");
        }
    }

    private boolean isAcceptedFollower(Long viewerId, Long authorId) {
        return followRepository.existsByFollowerIdAndFollowingIdAndStatus(viewerId, authorId, FollowStatus.ACCEPTED);
    }
}
