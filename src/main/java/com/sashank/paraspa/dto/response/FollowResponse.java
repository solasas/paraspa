package com.sashank.paraspa.dto.response;

import com.sashank.paraspa.enums.FollowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowResponse {
    private Long id;
    private UserResponse follower;
    private UserResponse following;
    /** PENDING when the account is private and the owner has not answered yet; ACCEPTED otherwise. */
    private FollowStatus status;
    private LocalDateTime createdAt;
}
