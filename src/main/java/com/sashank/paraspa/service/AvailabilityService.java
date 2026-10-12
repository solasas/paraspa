package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.request.AddAvailabilityRequest;
import com.sashank.paraspa.dto.response.AvailabilityResponse;
import java.util.List;

public interface AvailabilityService {
    AvailabilityResponse addAvailability(Long userId, AddAvailabilityRequest request);
    List<AvailabilityResponse> getAvailability(Long userId);
    void removeAvailability(Long userId, Long availabilityId);
}
