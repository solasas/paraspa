package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.ChangePasswordRequest;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.RatingRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.repository.UserSkillRepository;
import com.sashank.paraspa.storage.StorageService;
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
    private StorageService storageService;

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


    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private void stubProfileLookups(Long userId, User user) {
        when(userSkillRepository.findByUserIdAndType(any(), any())).thenReturn(java.util.List.of());
        when(ratingRepository.getAverageRatingByUserId(userId)).thenReturn(null);
        when(ratingRepository.countByRatedToId(userId)).thenReturn(0L);
        when(dtoMapper.toUserSkillResponseList(any())).thenReturn(java.util.List.of());
    }

    @Test
    void updateAvatar_shouldThrowBadRequestException_whenFileMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));

        assertThatThrownBy(() -> userService.updateAvatar(1L,
                new org.springframework.mock.web.MockMultipartFile("file", new byte[0])))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Avatar file is required");

        verify(storageService, never()).store(any(), any(), any());
    }

    @Test
    void updateAvatar_shouldThrowBadRequestException_whenLargerThanTwoMegabytes() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        byte[] tooBig = new byte[(int) UserServiceImpl.MAX_AVATAR_BYTES + 1];
        System.arraycopy(PNG_BYTES, 0, tooBig, 0, PNG_BYTES.length);

        assertThatThrownBy(() -> userService.updateAvatar(1L,
                new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", tooBig)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Avatar must be at most 2 MB");

        verify(storageService, never()).store(any(), any(), any());
    }

    @Test
    void updateAvatar_shouldAcceptExactlyTwoMegabytes() {
        Long userId = 1L;
        User user = User.builder().id(userId).build();
        byte[] exactly = new byte[(int) UserServiceImpl.MAX_AVATAR_BYTES];
        System.arraycopy(PNG_BYTES, 0, exactly, 0, PNG_BYTES.length);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(storageService.store(any(), any(), any())).thenReturn("/files/avatars/x.png");
        stubProfileLookups(userId, user);

        userService.updateAvatar(userId,
                new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", exactly));

        verify(storageService).store(any(), any(), any());
    }

    @Test
    void updateAvatar_shouldRejectNonImageEvenWhenClientClaimsImageType() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();

        assertThatThrownBy(() -> userService.updateAvatar(1L,
                new org.springframework.mock.web.MockMultipartFile("file", "evil.png", "image/png", html)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Avatar must be a JPG, PNG or WebP image");

        verify(storageService, never()).store(any(), any(), any());
    }

    @Test
    void updateAvatar_shouldRejectGif() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));

        assertThatThrownBy(() -> userService.updateAvatar(1L,
                new org.springframework.mock.web.MockMultipartFile("file", "a.gif", "image/gif", "GIF89a......".getBytes())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateAvatar_shouldStoreFileUnderUserScopedNameWithDetectedExtension() {
        Long userId = 7L;
        User user = User.builder().id(userId).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(storageService.store(any(), any(), any())).thenReturn("/files/avatars/7-new.png");
        stubProfileLookups(userId, user);

        // declared as jpeg but really a PNG: the detected type wins for the extension
        userService.updateAvatar(userId,
                new org.springframework.mock.web.MockMultipartFile("file", "photo.jpg", "image/jpeg", PNG_BYTES));

        ArgumentCaptor<String> filename = ArgumentCaptor.forClass(String.class);
        verify(storageService).store(org.mockito.ArgumentMatchers.eq("avatars"), filename.capture(), any());
        assertThat(filename.getValue()).matches("7-[0-9a-f-]{36}\\.png");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getAvatarUrl()).isEqualTo("/files/avatars/7-new.png");
    }

    @Test
    void updateAvatar_shouldDeletePreviousAvatarAfterSavingNewOne() {
        Long userId = 1L;
        User user = User.builder().id(userId).avatarUrl("/files/avatars/old.png").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(storageService.store(any(), any(), any())).thenReturn("/files/avatars/new.png");
        stubProfileLookups(userId, user);

        userService.updateAvatar(userId,
                new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", PNG_BYTES));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(userRepository, storageService);
        order.verify(userRepository).save(user);
        order.verify(storageService).delete("/files/avatars/old.png");
    }

    @Test
    void updateAvatar_shouldNotTouchStorageWhenUserMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateAvatar(1L,
                new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", PNG_BYTES)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(storageService, never()).store(any(), any(), any());
    }
}
