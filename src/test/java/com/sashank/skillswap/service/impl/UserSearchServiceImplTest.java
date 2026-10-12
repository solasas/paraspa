package com.sashank.skillswap.service.impl;

import com.sashank.skillswap.dto.response.UserResponse;
import com.sashank.skillswap.entity.Skill;
import com.sashank.skillswap.entity.User;
import com.sashank.skillswap.entity.UserSkill;
import com.sashank.skillswap.enums.SkillLevel;
import com.sashank.skillswap.enums.SkillType;
import com.sashank.skillswap.repository.AvailabilityRepository;
import com.sashank.skillswap.repository.RatingRepository;
import com.sashank.skillswap.repository.SkillRepository;
import com.sashank.skillswap.repository.UserRepository;
import com.sashank.skillswap.repository.UserSkillRepository;
import com.sashank.skillswap.util.DtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSearchServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSkillRepository userSkillRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private DtoMapper dtoMapper;

    @InjectMocks
    private UserSearchServiceImpl userSearchService;

    @Test
    void searchUsers_shouldExcludeCurrentUser_whenNoFiltersGiven() {
        Long currentUserId = 1L;
        User currentUser = User.builder().id(currentUserId).build();
        User otherUser = User.builder().id(2L).build();

        when(userRepository.findAll()).thenReturn(List.of(currentUser, otherUser));
        when(ratingRepository.getAverageRatingByUserId(anyLong())).thenReturn(null);
        when(ratingRepository.countByRatedToId(anyLong())).thenReturn(0L);
        when(dtoMapper.toUserResponseWithRatings(otherUser, 0.0, 0L))
                .thenReturn(UserResponse.builder().id(2L).averageRating(0.0).build());

        List<UserResponse> results = userSearchService.searchUsers(
                currentUserId, null, null, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(2L);
    }

    @Test
    void searchUsers_shouldFilterBySkillNameAndLevel() {
        Long currentUserId = 1L;
        User teacher = User.builder().id(2L).build();
        User nonMatchingLevelTeacher = User.builder().id(3L).build();

        Skill guitar = Skill.builder().id(10L).name("Guitar").build();
        UserSkill advancedGuitar = UserSkill.builder().user(teacher).skill(guitar).type(SkillType.TEACH).level(SkillLevel.ADVANCED).build();
        UserSkill beginnerGuitar = UserSkill.builder().user(nonMatchingLevelTeacher).skill(guitar).type(SkillType.TEACH).level(SkillLevel.BEGINNER).build();

        when(skillRepository.findByNameContainingIgnoreCase("Guitar")).thenReturn(List.of(guitar));
        when(userSkillRepository.findBySkillInAndType(List.of(guitar), SkillType.TEACH))
                .thenReturn(List.of(advancedGuitar, beginnerGuitar));
        when(ratingRepository.getAverageRatingByUserId(2L)).thenReturn(4.5);
        when(ratingRepository.countByRatedToId(2L)).thenReturn(3L);
        when(dtoMapper.toUserResponseWithRatings(teacher, 4.5, 3L))
                .thenReturn(UserResponse.builder().id(2L).averageRating(4.5).build());

        List<UserResponse> results = userSearchService.searchUsers(
                currentUserId, "Guitar", null, SkillLevel.ADVANCED, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(2L);
    }

    @Test
    void searchUsers_shouldFilterByMinRating() {
        Long currentUserId = 1L;
        User lowRated = User.builder().id(2L).build();
        User highRated = User.builder().id(3L).build();

        when(userRepository.findAll()).thenReturn(List.of(lowRated, highRated));
        when(ratingRepository.getAverageRatingByUserId(2L)).thenReturn(2.0);
        when(ratingRepository.countByRatedToId(2L)).thenReturn(5L);
        when(ratingRepository.getAverageRatingByUserId(3L)).thenReturn(4.8);
        when(ratingRepository.countByRatedToId(3L)).thenReturn(5L);
        when(dtoMapper.toUserResponseWithRatings(lowRated, 2.0, 5L))
                .thenReturn(UserResponse.builder().id(2L).averageRating(2.0).build());
        when(dtoMapper.toUserResponseWithRatings(highRated, 4.8, 5L))
                .thenReturn(UserResponse.builder().id(3L).averageRating(4.8).build());

        List<UserResponse> results = userSearchService.searchUsers(
                currentUserId, null, null, null, 4.0, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(3L);
    }

    @Test
    void searchUsers_shouldFilterByAvailableOnly() {
        Long currentUserId = 1L;
        User available = User.builder().id(2L).build();
        User unavailable = User.builder().id(3L).build();

        when(userRepository.findAll()).thenReturn(List.of(available, unavailable));
        when(availabilityRepository.existsByUserId(2L)).thenReturn(true);
        when(availabilityRepository.existsByUserId(3L)).thenReturn(false);
        when(ratingRepository.getAverageRatingByUserId(2L)).thenReturn(null);
        when(ratingRepository.countByRatedToId(2L)).thenReturn(0L);
        when(dtoMapper.toUserResponseWithRatings(available, 0.0, 0L))
                .thenReturn(UserResponse.builder().id(2L).averageRating(0.0).build());

        List<UserResponse> results = userSearchService.searchUsers(
                currentUserId, null, null, null, null, true);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(2L);
    }

    @Test
    void searchUsers_shouldFilterByCity() {
        Long currentUserId = 1L;
        User inCity = User.builder().id(2L).city("San Francisco").build();
        User elsewhere = User.builder().id(3L).city("Boston").build();

        when(userRepository.findAll()).thenReturn(List.of(inCity, elsewhere));
        when(ratingRepository.getAverageRatingByUserId(2L)).thenReturn(null);
        when(ratingRepository.countByRatedToId(2L)).thenReturn(0L);
        when(dtoMapper.toUserResponseWithRatings(inCity, 0.0, 0L))
                .thenReturn(UserResponse.builder().id(2L).averageRating(0.0).build());

        List<UserResponse> results = userSearchService.searchUsers(
                currentUserId, null, "francisco", null, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(2L);
    }

    @Test
    void searchUsers_shouldNotExposeEmail() {
        User otherUser = User.builder().id(2L).build();

        when(userRepository.findAll()).thenReturn(List.of(otherUser));
        when(ratingRepository.getAverageRatingByUserId(anyLong())).thenReturn(null);
        when(ratingRepository.countByRatedToId(anyLong())).thenReturn(0L);
        when(dtoMapper.toUserResponseWithRatings(otherUser, 0.0, 0L))
                .thenReturn(UserResponse.builder().id(2L).email("other@example.com").averageRating(0.0).build());

        List<UserResponse> results = userSearchService.searchUsers(1L, null, null, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEmail()).isNull();
    }
}
