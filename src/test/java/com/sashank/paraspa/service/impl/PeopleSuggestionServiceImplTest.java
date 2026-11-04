package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.MatchResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.repository.FollowRepository;
import com.sashank.paraspa.service.MatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeopleSuggestionServiceImplTest {

    @Mock private MatchService matchService;
    @Mock private FollowRepository followRepository;

    @InjectMocks
    private PeopleSuggestionServiceImpl suggestionService;

    private static MatchResponse match(long userId, boolean mutual) {
        return MatchResponse.builder().user(UserResponse.builder().id(userId).build()).isMutual(mutual).build();
    }

    private static List<Long> userIds(List<MatchResponse> matches) {
        return matches.stream().map(m -> m.getUser().getId()).toList();
    }

    @Test
    void getSuggestedPeople_leavesOutEveryoneIAlreadyFollow() {
        when(followRepository.findFollowedOrRequestedIds(1L)).thenReturn(List.of(3L, 5L));
        when(matchService.findMatches(1L)).thenReturn(List.of(match(2L, true), match(3L, true), match(4L, false), match(5L, false)));

        assertThat(userIds(suggestionService.getSuggestedPeople(1L, 0, 20))).containsExactly(2L, 4L);
    }

    @Test
    void getSuggestedPeople_alsoLeavesOutPeopleIHaveAskedToFollow_aPendingRequestIsNotASuggestion() {
        // findFollowedOrRequestedIds returns accepted follows AND pending requests
        when(followRepository.findFollowedOrRequestedIds(1L)).thenReturn(List.of(3L));
        when(matchService.findMatches(1L)).thenReturn(List.of(match(2L, false), match(3L, true)));

        assertThat(userIds(suggestionService.getSuggestedPeople(1L, 0, 20))).containsExactly(2L);
    }

    @Test
    void getSuggestedPeople_keepsTheMatchOrder_mutualMatchesFirst() {
        when(followRepository.findFollowedOrRequestedIds(1L)).thenReturn(List.of());
        when(matchService.findMatches(1L)).thenReturn(List.of(match(7L, true), match(2L, true), match(9L, false), match(4L, false)));

        assertThat(userIds(suggestionService.getSuggestedPeople(1L, 0, 20))).containsExactly(7L, 2L, 9L, 4L);
    }

    @Test
    void getSuggestedPeople_followingASuggestedPersonRemovesThemFromTheNextRequest() {
        when(matchService.findMatches(1L)).thenReturn(List.of(match(2L, true), match(3L, false)));
        when(followRepository.findFollowedOrRequestedIds(1L)).thenReturn(List.of(), List.of(2L));

        assertThat(userIds(suggestionService.getSuggestedPeople(1L, 0, 20))).containsExactly(2L, 3L);
        assertThat(userIds(suggestionService.getSuggestedPeople(1L, 0, 20))).containsExactly(3L);
    }

    @Test
    void getSuggestedPeople_isEmptyWhenEveryMatchIsAlreadyFollowedOrThereAreNone() {
        when(followRepository.findFollowedOrRequestedIds(1L)).thenReturn(List.of(2L));
        when(matchService.findMatches(1L)).thenReturn(List.of(match(2L, true)));

        assertThat(suggestionService.getSuggestedPeople(1L, 0, 20)).isEmpty();
    }

    @Test
    void getSuggestedPeople_pagesTheFilteredList_notTheOriginalOne() {
        // 10 matches; ids 1-3 are already followed. Page size 4 must give 4,5,6,7 then 8,9,10 (not a page with holes).
        when(followRepository.findFollowedOrRequestedIds(99L)).thenReturn(List.of(1L, 2L, 3L));
        when(matchService.findMatches(99L)).thenReturn(IntStream.rangeClosed(1, 10).mapToObj(i -> match(i, false)).toList());

        assertThat(userIds(suggestionService.getSuggestedPeople(99L, 0, 4))).containsExactly(4L, 5L, 6L, 7L);
        assertThat(userIds(suggestionService.getSuggestedPeople(99L, 1, 4))).containsExactly(8L, 9L, 10L);
        assertThat(suggestionService.getSuggestedPeople(99L, 2, 4)).isEmpty();
    }

    @Test
    void getSuggestedPeople_rejectsBadPagingBeforeDoingAnyWork() {
        assertThatThrownBy(() -> suggestionService.getSuggestedPeople(1L, 0, 101)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> suggestionService.getSuggestedPeople(1L, -1, 20)).isInstanceOf(BadRequestException.class);

        verify(matchService, never()).findMatches(1L);
    }
}
