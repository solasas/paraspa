package com.sashank.skillswap.service.impl;

import com.sashank.skillswap.dto.request.AddUserSkillRequest;
import com.sashank.skillswap.dto.response.UserSkillResponse;
import com.sashank.skillswap.entity.Skill;
import com.sashank.skillswap.entity.User;
import com.sashank.skillswap.entity.UserSkill;
import com.sashank.skillswap.enums.SkillLevel;
import com.sashank.skillswap.enums.SkillType;
import com.sashank.skillswap.exception.AlreadyExistsException;
import com.sashank.skillswap.repository.SkillRepository;
import com.sashank.skillswap.repository.UserRepository;
import com.sashank.skillswap.repository.UserSkillRepository;
import com.sashank.skillswap.util.DtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSkillServiceImplTest {

    @Mock
    private UserSkillRepository userSkillRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private DtoMapper dtoMapper;

    @InjectMocks
    private UserSkillServiceImpl userSkillService;

    private AddUserSkillRequest request() {
        return AddUserSkillRequest.builder()
                .skillId(10L)
                .type(SkillType.TEACH)
                .level(SkillLevel.BEGINNER)
                .build();
    }

    @Test
    void addUserSkill_shouldThrowAlreadyExists_whenSkillAlreadyListedWithSameType() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(skillRepository.findById(10L)).thenReturn(Optional.of(Skill.builder().id(10L).build()));
        when(userSkillRepository.existsByUserIdAndSkillIdAndType(1L, 10L, SkillType.TEACH)).thenReturn(true);

        assertThatThrownBy(() -> userSkillService.addUserSkill(1L, request()))
                .isInstanceOf(AlreadyExistsException.class);

        verify(userSkillRepository, never()).save(any());
    }

    @Test
    void addUserSkill_shouldSave_whenNotAlreadyListed() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(skillRepository.findById(10L)).thenReturn(Optional.of(Skill.builder().id(10L).build()));
        when(userSkillRepository.existsByUserIdAndSkillIdAndType(1L, 10L, SkillType.TEACH)).thenReturn(false);
        when(userSkillRepository.save(any(UserSkill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toUserSkillResponse(any(UserSkill.class))).thenReturn(UserSkillResponse.builder().build());

        userSkillService.addUserSkill(1L, request());

        verify(userSkillRepository).save(any(UserSkill.class));
    }
}
