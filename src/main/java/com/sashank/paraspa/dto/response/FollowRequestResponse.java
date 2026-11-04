package com.sashank.paraspa.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** A request to follow my private account that is waiting for my answer. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowRequestResponse {
    /** Use this id to accept or reject the request. */
    private Long id;
    private UserResponse requester;
    private LocalDateTime createdAt;
}
