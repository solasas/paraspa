package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.response.PublicProfileResponse;

public interface PublicProfileService {
    PublicProfileResponse getPublicProfile(Long viewerId, Long userId);
}
