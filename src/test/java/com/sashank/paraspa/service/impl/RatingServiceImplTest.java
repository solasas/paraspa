package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.CreateRatingRequest;
import com.sashank.paraspa.dto.response.RatingResponse;
import com.sashank.paraspa.dto.response.RatingSummaryResponse;
import com.sashank.paraspa.entity.Rating;
import com.sashank.paraspa.entity.Session;
import com.sashank.paraspa.entity.SkillExchange;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.SessionStatus;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.repository.RatingRepository;
import com.sashank.paraspa.repository.SessionRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.util.DtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceImplTest {

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DtoMapper dtoMapper;

    @InjectMocks
    private RatingServiceImpl ratingService;

    @Test
    void rateSession_shouldThrowBadRequestException_whenSessionNotCompleted() {
        Long userId = 1L;
        Long sessionId = 100L;

        User ratedBy = User.builder().id(userId).build();
        Session session = Session.builder()
                .id(sessionId)
                .status(SessionStatus.SCHEDULED)
                .build();

        CreateRatingRequest request = CreateRatingRequest.builder().stars(5).review("Great session").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(ratedBy));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> ratingService.rateSession(userId, sessionId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Can only rate completed sessions");

        verify(ratingRepository, never()).save(any());
    }

    @Test
    void rateSession_shouldThrowBadRequestException_whenAlreadyRatedByUser() {
        Long userId = 1L;
        Long sessionId = 100L;

        User ratedBy = User.builder().id(userId).build();
        User receiver = User.builder().id(2L).build();
        SkillExchange exchange = SkillExchange.builder()
                .requester(ratedBy)
                .receiver(receiver)
                .build();
        Session session = Session.builder()
                .id(sessionId)
                .exchange(exchange)
                .status(SessionStatus.COMPLETED)
                .build();

        CreateRatingRequest request = CreateRatingRequest.builder().stars(5).review("Great session").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(ratedBy));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(ratingRepository.findBySessionIdAndRatedById(sessionId, userId))
                .thenReturn(Optional.of(Rating.builder().id(500L).build()));

        assertThatThrownBy(() -> ratingService.rateSession(userId, sessionId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You have already rated this session");

        verify(ratingRepository, never()).save(any());
    }

    @Test
    void rateSession_shouldSaveRatingWithCorrectStarsAndReview() {
        Long userId = 1L;
        Long sessionId = 100L;

        User ratedBy = User.builder().id(userId).build();
        User receiver = User.builder().id(2L).build();
        SkillExchange exchange = SkillExchange.builder()
                .requester(ratedBy)
                .receiver(receiver)
                .build();
        Session session = Session.builder()
                .id(sessionId)
                .exchange(exchange)
                .status(SessionStatus.COMPLETED)
                .build();

        CreateRatingRequest request = CreateRatingRequest.builder().stars(4).review("Very helpful").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(ratedBy));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(ratingRepository.findBySessionIdAndRatedById(sessionId, userId)).thenReturn(Optional.empty());
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toRatingResponse(any(Rating.class))).thenReturn(RatingResponse.builder().build());

        ratingService.rateSession(userId, sessionId, request);

        ArgumentCaptor<Rating> ratingCaptor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository).save(ratingCaptor.capture());

        Rating savedRating = ratingCaptor.getValue();
        assertThat(savedRating.getStars()).isEqualTo(4);
        assertThat(savedRating.getReview()).isEqualTo("Very helpful");
        assertThat(savedRating.getRatedBy()).isEqualTo(ratedBy);
        assertThat(savedRating.getRatedTo()).isEqualTo(receiver);
    }

    @ParameterizedTest(name = "stars={0} -> average={1}")
    @MethodSource("ratingScenarios")
    void getRatingSummary_shouldCalculateCorrectAverage(List<Integer> stars, double expectedAverage) {
        Long userId = 1L;
        User user = User.builder().id(userId).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(ratingRepository.getAverageRatingByUserId(userId)).thenReturn(expectedAverage);
        when(ratingRepository.countByRatedToId(userId)).thenReturn((long) stars.size());

        RatingSummaryResponse summary = ratingService.getRatingSummary(userId);

        assertThat(summary.getAverageStars()).isCloseTo(expectedAverage, within(0.0001));
        assertThat(summary.getTotalRatings()).isEqualTo((long) stars.size());
    }

    private static Stream<Arguments> ratingScenarios() {
        return Stream.of(
                Arguments.of(List.of(5, 3, 4), 4.0),
                Arguments.of(List.of(1, 2, 3, 4, 5), 3.0),
                Arguments.of(List.of(5, 5, 4), 14.0 / 3)
        );
    }
}
