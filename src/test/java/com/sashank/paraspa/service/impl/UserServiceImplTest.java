package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.ChangePasswordRequest;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.RatingRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.repository.UserSkillRepository;
import com.sashank.paraspa.util.DtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSkillRepository userSkillRepository;

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DtoMapper dtoMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void changePassword_shouldThrowResourceNotFoundException_whenUserDoesNotExist() {
        Long userId = 1L;
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("oldPass123")
                .newPassword("newPass456")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_shouldThrowBadRequestException_whenCurrentPasswordIsIncorrect() {
        Long userId = 1L;
        String storedHash = "stored-hash";
        User user = User.builder().id(userId).password(storedHash).build();

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("wrongPassword")
                .newPassword("newPass456")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", storedHash)).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Current password is incorrect");

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_shouldThrowBadRequestException_whenNewPasswordSameAsCurrent() {
        Long userId = 1L;
        String storedHash = "stored-hash";
        User user = User.builder().id(userId).password(storedHash).build();

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("samePassword")
                .newPassword("samePassword")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("samePassword", storedHash)).thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("New password must be different from the current password");

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_shouldEncodeAndSaveNewPassword_whenValid() {
        Long userId = 1L;
        String storedHash = "stored-hash";
        String newEncodedHash = "new-encoded-hash";
        User user = User.builder().id(userId).password(storedHash).build();

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("oldPass123")
                .newPassword("newPass456")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass123", storedHash)).thenReturn(true);
        when(passwordEncoder.matches("newPass456", storedHash)).thenReturn(false);
        when(passwordEncoder.encode("newPass456")).thenReturn(newEncodedHash);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.changePassword(userId, request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo(newEncodedHash);
    }
}
