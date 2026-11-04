package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.MatchResponse;
import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.entity.UserSkill;
import com.sashank.paraspa.enums.SkillType;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.repository.UserSkillRepository;
import com.sashank.paraspa.util.DtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
                    return com.sashank.paraspa.dto.response.UserResponse.builder().id(user.getId()).build();
                });
        when(dtoMapper.toSkillResponse(org.mockito.ArgumentMatchers.any(Skill.class)))
                .thenAnswer(invocation -> {
                    Skill skill = invocation.getArgument(0);
                    return com.sashank.paraspa.dto.response.SkillResponse.builder().id(skill.getId()).build();
                });
    }

    /** The batched lookup: all rows of one type for a set of skills, with their users, in one call. */
    private void stubBatch(Set<Long> skillIds, SkillType type, UserSkill... rows) {
        when(userSkillRepository.findWithUserBySkillIdInAndType(eq(skillIds), eq(type))).thenReturn(List.of(rows));
    }

    @Test
    void findMatches_shouldReturnMatch_whenUserTeachesSkillOtherUserWantsToLearn() {
        Long userId = 1L;
        Skill guitar = Skill.builder().id(10L).build();

        User userA = User.builder().id(userId).build();
        User userB = User.builder().id(2L).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(userA));
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN)).thenReturn(List.of());
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH)).thenReturn(List.of(teach(userA, guitar)));
        // I teach guitar, so look for people who want to learn guitar: B does
        stubBatch(Set.of(10L), SkillType.LEARN, learn(userB, guitar));

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
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN)).thenReturn(List.of(learn(userA, piano)));
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH)).thenReturn(List.of(teach(userA, guitar)));
        // I want piano: B and C teach it. I teach guitar: B wants it.
        stubBatch(Set.of(20L), SkillType.TEACH, teach(userB, piano), teach(userC, piano));
        stubBatch(Set.of(10L), SkillType.LEARN, learn(userB, guitar));

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
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN)).thenReturn(List.of(learn(userA, guitar)));
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH)).thenReturn(List.of(teach(userA, guitar)));
        // the batched queries find the current user's own rows too (they teach and learn guitar); they must be ignored
        stubBatch(Set.of(10L), SkillType.TEACH, teach(userA, guitar));
        stubBatch(Set.of(10L), SkillType.LEARN, learn(userA, guitar));

        List<MatchResponse> matches = matchService.findMatches(userId);

        assertThat(matches).isEmpty();
    }

    @Test
    void findMatches_shouldReturnEmptyList_whenNoComplementarySkillsExist() {
        Long userId = 1L;
        Skill guitar = Skill.builder().id(10L).build();

        User userA = User.builder().id(userId).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(userA));
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN)).thenReturn(List.of());
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH)).thenReturn(List.of(teach(userA, guitar)));
        // nobody (e.g. a potter who teaches and learns pottery) wants to learn guitar
        stubBatch(Set.of(10L), SkillType.LEARN);

        assertThat(matchService.findMatches(userId)).isEmpty();
    }

    @Test
    void findMatches_shouldListMutualMatchesFirst_thenByUserId() {
        Long userId = 1L;
        Skill guitar = Skill.builder().id(10L).build();
        Skill piano = Skill.builder().id(20L).build();
        User me = User.builder().id(userId).build();
        User u2 = User.builder().id(2L).build();
        User u3 = User.builder().id(3L).build();
        User u4 = User.builder().id(4L).build();
        User u5 = User.builder().id(5L).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(me));
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN)).thenReturn(List.of(learn(me, piano)));
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH)).thenReturn(List.of(teach(me, guitar)));
        // rows come back in arbitrary order from the database
        stubBatch(Set.of(20L), SkillType.TEACH, teach(u5, piano), teach(u2, piano), teach(u4, piano));
        stubBatch(Set.of(10L), SkillType.LEARN, learn(u4, guitar), learn(u3, guitar), learn(u5, guitar));
        stubDtoMapper();

        List<MatchResponse> matches = matchService.findMatches(userId);

        // mutual: 4 and 5 (they teach piano and want guitar); one-sided: 2 (teaches piano), 3 (wants guitar)
        assertThat(matches).extracting(m -> m.getUser().getId()).containsExactly(4L, 5L, 2L, 3L);
        assertThat(matches).extracting(MatchResponse::isMutual).containsExactly(true, true, false, false);
    }

    @Test
    void findMatches_shouldNeverLoadAllUsersOrAskPerUser() {
        Long userId = 1L;
        Skill guitar = Skill.builder().id(10L).build();
        User me = User.builder().id(userId).build();
        User other = User.builder().id(2L).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(me));
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN)).thenReturn(List.of(learn(me, guitar)));
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH)).thenReturn(List.of(teach(me, guitar)));
        stubBatch(Set.of(10L), SkillType.TEACH, teach(other, guitar));
        stubBatch(Set.of(10L), SkillType.LEARN, learn(other, guitar));
        stubDtoMapper();

        matchService.findMatches(userId);

        // the old implementation did findAll() plus two findByUserIdAndType calls for every user in the system
        verify(userRepository, never()).findAll();
        verify(userSkillRepository, times(2)).findByUserIdAndType(anyLong(), any());       // only the current user's two lists
        verify(userSkillRepository, times(2)).findWithUserBySkillIdInAndType(any(), any()); // the two batched lookups
    }

    @Test
    void findMatches_shouldSkipTheBatchedQueryForAnEmptySkillList_andReturnNothingForAUserWithNoSkills() {
        User me = User.builder().id(1L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userSkillRepository.findByUserIdAndType(1L, SkillType.LEARN)).thenReturn(List.of());
        when(userSkillRepository.findByUserIdAndType(1L, SkillType.TEACH)).thenReturn(List.of());

        assertThat(matchService.findMatches(1L)).isEmpty();

        // an empty IN () list would be invalid SQL, so no batched query may run
        verify(userSkillRepository, never()).findWithUserBySkillIdInAndType(any(), any());
    }

    @Test
    void findMatches_shouldThrowResourceNotFoundException_whenCurrentUserDoesNotExist() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> matchService.findMatches(9L))
                .isInstanceOf(com.sashank.paraspa.exception.ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void findMatches_shouldListEachCandidateOnceEvenWhenTheyMatchInBothDirections() {
        Long userId = 1L;
        Skill guitar = Skill.builder().id(10L).build();
        Skill piano = Skill.builder().id(20L).build();
        Skill drums = Skill.builder().id(30L).build();
        User me = User.builder().id(userId).build();
        User other = User.builder().id(2L).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(me));
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN)).thenReturn(List.of(learn(me, piano), learn(me, drums)));
        when(userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH)).thenReturn(List.of(teach(me, guitar)));
        stubBatch(Set.of(20L, 30L), SkillType.TEACH, teach(other, piano), teach(other, drums));
        stubBatch(Set.of(10L), SkillType.LEARN, learn(other, guitar));
        stubDtoMapper();

        List<MatchResponse> matches = matchService.findMatches(userId);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getTheyCanTeachMe()).extracting("id").containsExactlyInAnyOrder(20L, 30L);
        assertThat(matches.get(0).getICanTeachThem()).extracting("id").containsExactly(10L);
        assertThat(matches.get(0).isMutual()).isTrue();
    }

    @Test
    void checkMatch_shouldNotExposeOtherUsersEmail() {
        User other = User.builder().id(2L).build();

        when(userRepository.findById(2L)).thenReturn(java.util.Optional.of(other));
        when(userSkillRepository.findByUserIdAndType(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());
        when(dtoMapper.toUserResponse(other))
                .thenReturn(com.sashank.paraspa.dto.response.UserResponse.builder().id(2L).email("other@example.com").build());

        com.sashank.paraspa.dto.response.MatchResponse match = matchService.checkMatch(1L, 2L);

        org.assertj.core.api.Assertions.assertThat(match.getUser().getId()).isEqualTo(2L);
        org.assertj.core.api.Assertions.assertThat(match.getUser().getEmail()).isNull();
    }
}
