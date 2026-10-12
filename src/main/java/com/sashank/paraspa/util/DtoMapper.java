package com.sashank.paraspa.util;

import com.sashank.paraspa.dto.response.*;
import com.sashank.paraspa.entity.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DtoMapper {

    public UserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .bio(user.getBio())
                .city(user.getCity())
                .build();
    }

    public UserResponse toUserResponseWithRatings(User user, Double avgRating, Long totalRatings) {
        UserResponse response = toUserResponse(user);
        response.setAverageRating(avgRating);
        response.setTotalRatings(totalRatings);
        return response;
    }

    public SkillResponse toSkillResponse(Skill skill) {
        if (skill == null) {
            return null;
        }
        return SkillResponse.builder()
                .id(skill.getId())
                .name(skill.getName())
                .category(skill.getCategory())
                .description(skill.getDescription())
                .build();
    }

    public UserSkillResponse toUserSkillResponse(UserSkill userSkill) {
        if (userSkill == null) {
            return null;
        }
        return UserSkillResponse.builder()
                .id(userSkill.getId())
                .skill(toSkillResponse(userSkill.getSkill()))
                .type(userSkill.getType())
                .level(userSkill.getLevel())
                .build();
    }

    public ExchangeResponse toExchangeResponse(SkillExchange exchange) {
        if (exchange == null) {
            return null;
        }
        return ExchangeResponse.builder()
                .id(exchange.getId())
                .requester(toUserResponse(exchange.getRequester()))
                .receiver(toUserResponse(exchange.getReceiver()))
                .offeredSkill(toSkillResponse(exchange.getOfferedSkill()))
                .wantedSkill(toSkillResponse(exchange.getWantedSkill()))
                .status(exchange.getStatus())
                .message(exchange.getMessage())
                .createdAt(exchange.getCreatedAt())
                .updatedAt(exchange.getUpdatedAt())
                .build();
    }

    public SessionResponse toSessionResponse(Session session) {
        if (session == null) {
            return null;
        }
        return SessionResponse.builder()
                .id(session.getId())
                .exchange(toExchangeResponse(session.getExchange()))
                .scheduledBy(toUserResponse(session.getScheduledBy()))
                .dateTime(session.getDateTime())
                .durationMinutes(session.getDurationMinutes())
                .mode(session.getMode())
                .meetLink(session.getMeetLink())
                .location(session.getLocation())
                .notes(session.getNotes())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .build();
    }

    public RatingResponse toRatingResponse(Rating rating) {
        if (rating == null) {
            return null;
        }
        return RatingResponse.builder()
                .id(rating.getId())
                .session(toSessionResponse(rating.getSession()))
                .ratedBy(toUserResponse(rating.getRatedBy()))
                .ratedTo(toUserResponse(rating.getRatedTo()))
                .stars(rating.getStars())
                .review(rating.getReview())
                .createdAt(rating.getCreatedAt())
                .build();
    }

    public MessageResponse toMessageResponse(Message message) {
        if (message == null) {
            return null;
        }
        return MessageResponse.builder()
                .id(message.getId())
                .sender(toUserResponse(message.getSender()))
                .receiver(toUserResponse(message.getReceiver()))
                .content(message.getContent())
                .read(message.isRead())
                .createdAt(message.getCreatedAt())
                .build();
    }

    public AvailabilityResponse toAvailabilityResponse(Availability availability) {
        if (availability == null) {
            return null;
        }
        return AvailabilityResponse.builder()
                .id(availability.getId())
                .dayOfWeek(availability.getDayOfWeek())
                .startTime(availability.getStartTime())
                .endTime(availability.getEndTime())
                .build();
    }

    public RescheduleRequestResponse toRescheduleRequestResponse(RescheduleRequest rescheduleRequest) {
        if (rescheduleRequest == null) {
            return null;
        }
        return RescheduleRequestResponse.builder()
                .id(rescheduleRequest.getId())
                .sessionId(rescheduleRequest.getSession().getId())
                .requestedBy(toUserResponse(rescheduleRequest.getRequestedBy()))
                .proposedDateTime(rescheduleRequest.getProposedDateTime())
                .reason(rescheduleRequest.getReason())
                .status(rescheduleRequest.getStatus())
                .createdAt(rescheduleRequest.getCreatedAt())
                .build();
    }

    public List<UserSkillResponse> toUserSkillResponseList(List<UserSkill> skills) {
        return skills.stream()
                .map(this::toUserSkillResponse)
                .collect(Collectors.toList());
    }

    public List<SkillResponse> toSkillResponseList(List<Skill> skills) {
        return skills.stream()
                .map(this::toSkillResponse)
                .collect(Collectors.toList());
    }
}

