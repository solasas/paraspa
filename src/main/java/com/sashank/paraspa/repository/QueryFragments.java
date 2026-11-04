package com.sashank.paraspa.repository;

/**
 * JPQL pieces shared by the queries that must hide blocked users and private accounts. They are constants so they can
 * be used inside {@code @Query} annotations, and they live in one place so every listing applies exactly the same rule.
 * All of them expect a named parameter {@code :viewerId}: the logged-in user.
 */
public final class QueryFragments {

    private QueryFragments() {
    }

    public static final String ACCEPTED = "com.sashank.paraspa.enums.FollowStatus.ACCEPTED";
    public static final String PENDING = "com.sashank.paraspa.enums.FollowStatus.PENDING";

    /** No block in either direction between the viewer and the author of post {@code p}. */
    public static final String POST_AUTHOR_NOT_BLOCKED =
            "NOT EXISTS (SELECT 1 FROM Block b WHERE (b.blocker.id = :viewerId AND b.blocked.id = p.author.id) "
                    + "OR (b.blocked.id = :viewerId AND b.blocker.id = p.author.id))";

    /** The viewer may see post {@code p}: its author is public, is the viewer, or the viewer is an accepted follower. */
    public static final String POST_AUTHOR_VISIBLE =
            "(p.author.isPrivate = false OR p.author.id = :viewerId OR EXISTS (SELECT 1 FROM Follow f "
                    + "WHERE f.follower.id = :viewerId AND f.following.id = p.author.id AND f.status = " + ACCEPTED + "))";

    /** Posts the viewer is allowed to see in a general listing: not blocked, and public or followed. */
    public static final String POST_VISIBLE = POST_AUTHOR_NOT_BLOCKED + " AND " + POST_AUTHOR_VISIBLE;

    /** No block in either direction between the viewer and the author of comment {@code c}. */
    public static final String COMMENT_AUTHOR_NOT_BLOCKED =
            "NOT EXISTS (SELECT 1 FROM Block b WHERE (b.blocker.id = :viewerId AND b.blocked.id = c.author.id) "
                    + "OR (b.blocked.id = :viewerId AND b.blocker.id = c.author.id))";

    /** No block in either direction between the viewer and the user who made like {@code l}. */
    public static final String LIKER_NOT_BLOCKED =
            "NOT EXISTS (SELECT 1 FROM Block b WHERE (b.blocker.id = :viewerId AND b.blocked.id = l.user.id) "
                    + "OR (b.blocked.id = :viewerId AND b.blocker.id = l.user.id))";
}
