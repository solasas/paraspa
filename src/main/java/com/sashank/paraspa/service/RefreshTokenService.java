package com.sashank.paraspa.service;

import com.sashank.paraspa.entity.User;

/** Issues, rotates and revokes refresh tokens. The raw token is only ever returned from here, never stored. */
public interface RefreshTokenService {

    /** A new token for the user; returns the raw value to hand to the client. */
    String issue(User user);

    /**
     * Uses a refresh token once: revokes it and returns the user it belonged to. A token that is unknown, expired or
     * already used is a 401; presenting an already-used one is treated as theft and revokes every token of that user.
     */
    User consume(String rawToken);

    /** Logout: revokes this token. Unknown or already revoked tokens are ignored (idempotent). */
    void revoke(String rawToken);

    /** Revokes every active token of the user (password change). */
    void revokeAll(Long userId);

    /** Deletes tokens that expired more than the grace period ago. */
    int purgeExpired();
}
