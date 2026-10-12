package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.CreateRatingRequest;
import com.sashank.paraspa.dto.response.RatingResponse;
import com.sashank.paraspa.dto.response.RatingSummaryResponse;
import com.sashank.paraspa.entity.Rating;
import com.sashank.paraspa.entity.Session;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.SessionStatus;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.RatingRepository;
import com.sashank.paraspa.repository.SessionRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.RatingService;
import com.sashank.paraspa.util.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RatingServiceImpl implements RatingService {

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    public RatingResponse rateSession(Long userId, Long sessionId, CreateRatingRequest request) {
        User ratedBy = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!session.getStatus().equals(SessionStatus.COMPLETED)) {
            throw new BadRequestException("Can only rate completed sessions");
        }

        User otherParticipant = session.getExchange().getRequester().getId().equals(userId) ?
                session.getExchange().getReceiver() :
                session.getExchange().getRequester();

        if (ratingRepository.findBySessionIdAndRatedById(sessionId, userId).isPresent()) {
            throw new BadRequestException("You have already rated this session");
        }

        Rating rating = Rating.builder()
                .session(session)
                .ratedBy(ratedBy)
                .ratedTo(otherParticipant)
                .stars(request.getStars())
                .review(request.getReview())
                .build();

        rating = ratingRepository.save(rating);
        return dtoMapper.toRatingResponse(rating);
    }

    @Override
    public List<RatingResponse> getUserRatings(Long userId) {
        return ratingRepository.findByRatedToId(userId).stream()
                .map(dtoMapper::toRatingResponse)
                .toList();
    }

    @Override
    public RatingSummaryResponse getRatingSummary(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Double averageRating = ratingRepository.getAverageRatingByUserId(userId);
        long totalRatings = ratingRepository.countByRatedToId(userId);

        long oneStar = ratingRepository.countByUserAndStars(userId, 1);
        long twoStar = ratingRepository.countByUserAndStars(userId, 2);
        long threeStar = ratingRepository.countByUserAndStars(userId, 3);
        long fourStar = ratingRepository.countByUserAndStars(userId, 4);
        long fiveStar = ratingRepository.countByUserAndStars(userId, 5);

        RatingSummaryResponse.RatingBreakdown breakdown = RatingSummaryResponse.RatingBreakdown.builder()
                .oneStar(oneStar)
                .twoStar(twoStar)
                .threeStar(threeStar)
                .fourStar(fourStar)
                .fiveStar(fiveStar)
                .build();

        return RatingSummaryResponse.builder()
                .averageStars(averageRating != null ? averageRating : 0.0)
                .totalRatings(totalRatings)
                .breakdown(breakdown)
                .build();
    }

    @Override
    public List<RatingResponse> getMyReceivedRatings(Long userId) {
        return ratingRepository.findByRatedToId(userId).stream()
                .map(dtoMapper::toRatingResponse)
                .toList();
    }
}

