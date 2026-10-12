package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.AddAvailabilityRequest;
import com.sashank.paraspa.dto.response.AvailabilityResponse;
import com.sashank.paraspa.entity.Availability;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.AvailabilityRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.util.DtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceImplTest {

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DtoMapper dtoMapper;

    @InjectMocks
    private AvailabilityServiceImpl availabilityService;

    @Test
    void addAvailability_shouldThrowBadRequestException_whenStartTimeIsNotBeforeEndTime() {
        Long userId = 1L;
        User user = User.builder().id(userId).build();

        AddAvailabilityRequest request = AddAvailabilityRequest.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(17, 0))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> availabilityService.addAvailability(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Start time must be before end time");

        verify(availabilityRepository, never()).save(any());
    }

    @Test
    void addAvailability_shouldSaveSlot_whenTimesAreValid() {
        Long userId = 1L;
        User user = User.builder().id(userId).build();

        AddAvailabilityRequest request = AddAvailabilityRequest.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(17, 0))
                .endTime(LocalTime.of(19, 0))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(availabilityRepository.save(any(Availability.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toAvailabilityResponse(any(Availability.class))).thenReturn(AvailabilityResponse.builder().build());

        availabilityService.addAvailability(userId, request);

        ArgumentCaptor<Availability> captor = ArgumentCaptor.forClass(Availability.class);
        verify(availabilityRepository).save(captor.capture());

        Availability saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    void removeAvailability_shouldThrowResourceNotFoundException_whenSlotDoesNotBelongToUser() {
        Long userId = 1L;
        Long availabilityId = 10L;

        when(availabilityRepository.findByIdAndUserId(availabilityId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> availabilityService.removeAvailability(userId, availabilityId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Availability slot not found");

        verify(availabilityRepository, never()).delete(any());
    }

    @Test
    void removeAvailability_shouldDeleteSlot_whenOwnedByUser() {
        Long userId = 1L;
        Long availabilityId = 10L;
        Availability availability = Availability.builder().id(availabilityId).build();

        when(availabilityRepository.findByIdAndUserId(availabilityId, userId)).thenReturn(Optional.of(availability));

        availabilityService.removeAvailability(userId, availabilityId);

        verify(availabilityRepository).delete(availability);
    }
}
