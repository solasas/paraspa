package com.sashank.skillswap.service.impl;

import com.sashank.skillswap.dto.response.MatchResponse;
import com.sashank.skillswap.entity.Skill;
import com.sashank.skillswap.entity.User;
import com.sashank.skillswap.entity.UserSkill;
import com.sashank.skillswap.enums.SkillType;
import com.sashank.skillswap.repository.UserRepository;
import com.sashank.skillswap.repository.UserSkillRepository;
import com.sashank.skillswap.util.DtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSkillRepository userSkillRepository;

    @Mock
    private DtoMapper dtoMapper;

    @InjectMocks
    private MatchServiceImpl matchService;

    private static UserSkill teach(User user, Skill skill) {
        return UserSkill.builder().user(user).skill(skill).type(SkillType.TEACH).build();
    }

    private static UserSkill learn(User user, Skill skill) {
        return UserSkill.builder().user(user).skill(skill).type(SkillType.LEARN).build();
    }

    private void stubDtoMapper() {
        when(dtoMapper.toUserResponse(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    return com.sashank.skillswap.dto.response.UserResponse.builder().id(user.getId()).build();
                });
        when(dtoMapper.toSkillResponse(org.mockito.ArgumentMatchers.any(Skill.class)))
                .thenAnswer(invocation -> {
                    Skill skill = invocation.getArgument(0);
                    return com.sashank.skillswap.dto.response.SkillResponse.builder().id(skill.getId()).build();
                });
    }

    @Test
    void findMatches_shouldReturnMatch_whenUserTeachesSkillOtherUserWantsToLearn() {
        Long userId = 1L;
        Skill guitar = Skill.builder().id(10L).build();

        User userA = User.builder().id(userId).build();
        User userB = User.builder().id(2L).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(userA));
        when(userRepository.findAll()).thenReturn(List.of(userA, userB));

        when(userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN)).thenReturn(List.of());
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH)).thenReturn(List.of(teach(userA, guitar)));
        when(userSkillRepository.findByUserIdAndType(2L, SkillType.TEACH)).thenReturn(List.of());
        when(userSkillRepository.findByUserIdAndType(2L, SkillType.LEARN)).thenReturn(List.of(learn(userB, guitar)));

        stubDtoMapper();

        List<MatchResponse> matches = matchService.findMatches(userId);

        assertThat(matches).hasSize(1);
        MatchResponse match = matches.get(0);
        assertThat(match.getUser().getId()).isEqualTo(2L);
        assertThat(match.getICanTeachThem()).extracting("id").containsExactly(10L);
        assertThat(match.getTheyCanTeachMe()).isEmpty();
        assertThat(match.isMutual()).isFalse();
    }

    @Test
    void findMutualMatches_shouldReturnMatch_onlyWhenBothUsersTeachWhatTheOtherWants() {
        Long userId = 1L;
        Skill guitar = Skill.builder().id(10L).build();
        Skill piano = Skill.builder().id(20L).build();

        User userA = User.builder().id(userId).build();
        User userB = User.builder().id(2L).build();
        User userC = User.builder().id(3L).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(userA));
        when(userRepository.findAll()).thenReturn(List.of(userA, userB, userC));

        when(userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN)).thenReturn(List.of(learn(userA, piano)));
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH)).thenReturn(List.of(teach(userA, guitar)));

        when(userSkillRepository.findByUserIdAndType(2L, SkillType.TEACH)).thenReturn(List.of(teach(userB, piano)));
        when(userSkillRepository.findByUserIdAndType(2L, SkillType.LEARN)).thenReturn(List.of(learn(userB, guitar)));

        when(userSkillRepository.findByUserIdAndType(3L, SkillType.TEACH)).thenReturn(List.of(teach(userC, piano)));
        when(userSkillRepository.findByUserIdAndType(3L, SkillType.LEARN)).thenReturn(List.of());

        stubDtoMapper();

        List<MatchResponse> allMatches = matchService.findMatches(userId);
        List<MatchResponse> mutualMatches = matchService.findMutualMatches(userId);

        assertThat(allMatches).hasSize(2);
        assertThat(mutualMatches).hasSize(1);
        assertThat(mutualMatches.get(0).getUser().getId()).isEqualTo(2L);
        assertThat(mutualMatches.get(0).isMutual()).isTrue();
    }

    @Test
    void findMatches_shouldExcludeCurrentUserFromOwnMatchResults() {
        Long userId = 1L;
        Skill guitar = Skill.builder().id(10L).build();

        User userA = User.builder().id(userId).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(userA));
        when(userRepository.findAll()).thenReturn(List.of(userA));

        when(userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN)).thenReturn(List.of(learn(userA, guitar)));
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH)).thenReturn(List.of(teach(userA, guitar)));

        List<MatchResponse> matches = matchService.findMatches(userId);

        assertThat(matches).isEmpty();
    }

    @Test
    void findMatches_shouldReturnEmptyList_whenNoComplementarySkillsExist() {
        Long userId = 1L;
        Skill guitar = Skill.builder().id(10L).build();
        Skill pottery = Skill.builder().id(30L).build();

        User userA = User.builder().id(userId).build();
        User userB = User.builder().id(2L).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(userA));
        when(userRepository.findAll()).thenReturn(List.of(userA, userB));

        when(userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN)).thenReturn(List.of());
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH)).thenReturn(List.of(teach(userA, guitar)));

        when(userSkillRepository.findByUserIdAndType(2L, SkillType.TEACH)).thenReturn(List.of(teach(userB, pottery)));
        when(userSkillRepository.findByUserIdAndType(2L, SkillType.LEARN)).thenReturn(List.of(learn(userB, pottery)));

        List<MatchResponse> matches = matchService.findMatches(userId);

        assertThat(matches).isEmpty();
    }

    @Test
    void checkMatch_shouldNotExposeOtherUsersEmail() {
        User other = User.builder().id(2L).build();

        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(other));
        when(userSkillRepository.findByUserIdAndType(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());
        when(dtoMapper.toUserResponse(other))
                .thenReturn(com.sashank.skillswap.dto.response.UserResponse.builder().id(2L).email("other@example.com").build());

        com.sashank.skillswap.dto.response.MatchResponse match = matchService.checkMatch(1L, 2L);

        org.assertj.core.api.Assertions.assertThat(match.getUser().getId()).isEqualTo(2L);
        org.assertj.core.api.Assertions.assertThat(match.getUser().getEmail()).isNull();
    }
}
