package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.PublicProfileResponse;
import com.sashank.paraspa.dto.response.RatingSummaryResponse;
import com.sashank.paraspa.dto.response.UserSkillResponse;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.SkillType;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.FollowRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.repository.UserSkillRepository;
import com.sashank.paraspa.service.RatingService;
import com.sashank.paraspa.util.DtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicProfileServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSkillRepository userSkillRepository;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private RatingService ratingService;

    @Mock
    private DtoMapper dtoMapper;

    @InjectMocks
    private PublicProfileServiceImpl publicProfileService;

    @Test
    void getPublicProfile_shouldThrowResourceNotFoundException_whenUserMissing() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> publicProfileService.getPublicProfile(1L, 9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");

        verify(ratingService, never()).getRatingSummary(9L);
    }

    @Test
    void getPublicProfile_shouldCombineProfileRatingSkillsAndFollowCounts() {
        User user = User.builder().id(2L).name("Asha").email("asha@example.com").bio("Guitarist")
                .city("Pune").avatarUrl("/files/avatars/2-x.png").build();
        RatingSummaryResponse summary = RatingSummaryResponse.builder().averageStars(4.5).totalRatings(10L).build();
        List<UserSkillResponse> teach = List.of(UserSkillResponse.builder().id(1L).build());
        List<UserSkillResponse> learn = List.of(UserSkillResponse.builder().id(2L).build(), UserSkillResponse.builder().id(3L).build());

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(ratingService.getRatingSummary(2L)).thenReturn(summary);
        when(userSkillRepository.findByUserIdAndType(2L, SkillType.TEACH)).thenReturn(List.of());
        when(userSkillRepository.findByUserIdAndType(2L, SkillType.LEARN)).thenReturn(List.of());
        when(dtoMapper.toUserSkillResponseList(List.of())).thenReturn(teach, learn);
        when(followRepository.countByFollowingId(2L)).thenReturn(7L);
        when(followRepository.countByFollowerId(2L)).thenReturn(3L);
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);

        PublicProfileResponse profile = publicProfileService.getPublicProfile(1L, 2L);

        assertThat(profile.getId()).isEqualTo(2L);
        assertThat(profile.getName()).isEqualTo("Asha");
        assertThat(profile.getBio()).isEqualTo("Guitarist");
        assertThat(profile.getCity()).isEqualTo("Pune");
        assertThat(profile.getAvatarUrl()).isEqualTo("/files/avatars/2-x.png");
        assertThat(profile.getRatingSummary()).isSameAs(summary);
        assertThat(profile.getTeachSkills()).hasSize(1);
        assertThat(profile.getLearnSkills()).hasSize(2);
        assertThat(profile.getFollowersCount()).isEqualTo(7L);
        assertThat(profile.getFollowingCount()).isEqualTo(3L);
        assertThat(profile.isFollowedByMe()).isTrue();
    }

    @Test
    void publicProfileResponse_shouldHaveNoEmailOrPasswordField() {
        List<String> fields = Arrays.stream(PublicProfileResponse.class.getDeclaredFields())
                .map(Field::getName).toList();

        assertThat(fields).doesNotContain("email", "password", "role");
    }

    @Test
    void getPublicProfile_shouldNeverCarryTheEmailAnywhereInItsJson() throws Exception {
        User user = User.builder().id(2L).name("Asha").email("secret@example.com").build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(ratingService.getRatingSummary(2L)).thenReturn(RatingSummaryResponse.builder().build());
        when(userSkillRepository.findByUserIdAndType(2L, SkillType.TEACH)).thenReturn(List.of());
        when(userSkillRepository.findByUserIdAndType(2L, SkillType.LEARN)).thenReturn(List.of());
        when(dtoMapper.toUserSkillResponseList(List.of())).thenReturn(List.of());

        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(publicProfileService.getPublicProfile(1L, 2L));

        assertThat(json).doesNotContain("secret@example.com").doesNotContain("email");
    }
}
