package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.entity.Block;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.BlockRepository;
import com.sashank.paraspa.repository.FollowRepository;
import com.sashank.paraspa.repository.NotificationRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.util.DtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockServiceImplTest {

    @Mock private BlockRepository blockRepository;
    @Mock private FollowRepository followRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private DtoMapper dtoMapper;

    @InjectMocks
    private BlockServiceImpl blockService;

    // ================================================================ blocking

    @Test
    void block_recordsTheBlock_thenRemovesFollowsAndNotificationsInBothDirections() {
        when(userRepository.existsById(2L)).thenReturn(true);

        blockService.block(1L, 2L);

        InOrder order = inOrder(blockRepository, followRepository, notificationRepository);
        order.verify(blockRepository).insertIfAbsent(eq(1L), eq(2L), any(LocalDateTime.class));
        order.verify(followRepository).deleteBetween(1L, 2L);          // follows AND pending requests, both ways
        order.verify(notificationRepository).deleteBetween(1L, 2L);    // what they caused for each other
    }

    @Test
    void block_isIdempotent_theCleanupRunsAgainEvenIfTheBlockAlreadyExisted() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(blockRepository.insertIfAbsent(eq(1L), eq(2L), any(LocalDateTime.class))).thenReturn(1, 0);

        blockService.block(1L, 2L);
        blockService.block(1L, 2L);   // no error, and a retry also finishes any cleanup that failed the first time

        verify(followRepository, org.mockito.Mockito.times(2)).deleteBetween(1L, 2L);
        verify(notificationRepository, org.mockito.Mockito.times(2)).deleteBetween(1L, 2L);
    }

    @Test
    void block_yourself_is400_andNothingHappens() {
        assertThatThrownBy(() -> blockService.block(1L, 1L))
                .isInstanceOf(BadRequestException.class).hasMessage("You cannot block yourself");

        verifyNoInteractions(blockRepository, followRepository, notificationRepository);
    }

    @Test
    void block_missingUser_is404_andNothingHappens() {
        when(userRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> blockService.block(1L, 9L))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("User not found");

        verifyNoInteractions(blockRepository, followRepository, notificationRepository);
    }

    // ================================================================ unblocking

    @Test
    void unblock_removesTheBlock_butDoesNotRestoreFollows() {
        when(userRepository.existsById(2L)).thenReturn(true);

        blockService.unblock(1L, 2L);

        verify(blockRepository).deleteBlock(1L, 2L);
        verifyNoInteractions(followRepository);   // follows removed by the block stay removed
    }

    @Test
    void unblock_isIdempotent_whenThereIsNoBlock() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(blockRepository.deleteBlock(1L, 2L)).thenReturn(0);

        blockService.unblock(1L, 2L);   // no exception
    }

    @Test
    void unblock_missingUser_is404() {
        when(userRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> blockService.unblock(1L, 9L)).isInstanceOf(ResourceNotFoundException.class);

        verify(blockRepository, never()).deleteBlock(any(), any());
    }

    // ================================================================ listing

    @Test
    void getBlockedUsers_listsOnlyThePeopleIBlocked_asPublicUsers() {
        User ben = User.builder().id(2L).email("ben@example.com").build();
        UserResponse mapped = UserResponse.builder().id(2L).build();
        when(blockRepository.findByBlockerIdOrderByIdDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(Block.builder().blocker(User.builder().id(1L).build()).blocked(ben).build()));
        when(dtoMapper.toPublicUserResponse(ben)).thenReturn(mapped);

        assertThat(blockService.getBlockedUsers(1L, 0, 20)).containsExactly(mapped);
        verify(dtoMapper, never()).toUserResponse(any());   // never the variant that carries the email
    }

    @Test
    void getBlockedUsers_rejectsBadPaging() {
        assertThatThrownBy(() -> blockService.getBlockedUsers(1L, 0, 101)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> blockService.getBlockedUsers(1L, -1, 20)).isInstanceOf(BadRequestException.class);
    }

    // ================================================================ the hidden set and the interaction check

    @Test
    void getHiddenUserIds_isEveryoneBlockedEitherWay() {
        when(blockRepository.findHiddenUserIds(1L)).thenReturn(List.of(2L, 3L, 2L));

        assertThat(blockService.getHiddenUserIds(1L)).isEqualTo(Set.of(2L, 3L));
    }

    @Test
    void isBlockedEitherWay_checksBothDirections_andNeverForYourself() {
        when(blockRepository.existsBetween(1L, 2L)).thenReturn(true);

        assertThat(blockService.isBlockedEitherWay(1L, 2L)).isTrue();
        assertThat(blockService.isBlockedEitherWay(1L, 1L)).isFalse();
        verify(blockRepository, never()).existsBetween(1L, 1L);
    }

    @Test
    void requireNoBlock_passesWhenNeitherBlockedTheOther() {
        blockService.requireNoBlock(1L, 2L);
    }

    @Test
    void requireNoBlock_whenTheActorBlockedTheOtherPerson_tellsThemToUnblockFirst() {
        when(blockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> blockService.requireNoBlock(1L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You have blocked this user. Unblock them first.");
    }

    @Test
    void requireNoBlock_whenTheOtherPersonBlockedTheActor_looksLikeAMissingUser_soABlockCannotBeDetected() {
        when(blockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(false);
        when(blockRepository.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> blockService.requireNoBlock(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void requireNoBlock_aUserIsNeverBlockedFromThemselves() {
        blockService.requireNoBlock(1L, 1L);

        verifyNoInteractions(blockRepository);
    }
}
