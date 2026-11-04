package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.ChangePasswordRequest;
import com.sashank.paraspa.dto.request.UpdateProfileRequest;
import com.sashank.paraspa.dto.response.ProfileResponse;
import com.sashank.paraspa.dto.response.UserSkillResponse;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.entity.UserSkill;
import com.sashank.paraspa.enums.SkillType;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.RatingRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.repository.UserSkillRepository;
import com.sashank.paraspa.service.RefreshTokenService;
import com.sashank.paraspa.service.UserService;
import com.sashank.paraspa.storage.StorageService;
import com.sashank.paraspa.util.ImageFormat;
import com.sashank.paraspa.util.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.sashank.paraspa.repository.FollowRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    public static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Override
    public ProfileResponse getProfile(Long userId) {
        User user = getUserById(userId);

        List<UserSkill> teachSkills = userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH);
        List<UserSkill> learnSkills = userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN);

        Double avgRating = ratingRepository.getAverageRatingByUserId(userId);
        long totalRatings = ratingRepository.countByRatedToId(userId);

        return ProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .bio(user.getBio())
                .city(user.getCity())
                .avatarUrl(user.getAvatarUrl())
                .privateAccount(user.isPrivate())
                .teachSkills(dtoMapper.toUserSkillResponseList(teachSkills))
                .learnSkills(dtoMapper.toUserSkillResponseList(learnSkills))
                .averageRating(avgRating != null ? avgRating : 0.0)
                .totalRatings(totalRatings)
                .build();
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUserById(userId);

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getCity() != null) {
            user.setCity(request.getCity());
        }
        boolean becomesPublic = request.getPrivateAccount() != null && !request.getPrivateAccount() && user.isPrivate();
        if (request.getPrivateAccount() != null) {
            user.setPrivate(request.getPrivateAccount());
        }

        user = userRepository.save(user);

        if (becomesPublic) {
            // nothing is left to approve once anyone may follow: the people who were waiting are now followers
            followRepository.acceptAllPending(user.getId());
        }

        return getProfile(user.getId());
    }

    @Override
    public ProfileResponse updateAvatar(Long userId, MultipartFile file) {
        User user = getUserById(userId);

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Avatar file is required");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BadRequestException("Avatar must be at most 2 MB");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file");
        }

        ImageFormat format = ImageFormat.detect(content)
                .orElseThrow(() -> new BadRequestException("Avatar must be a JPG, PNG or WebP image"));

        String filename = userId + "-" + UUID.randomUUID() + "." + format.getExtension();
        String newUrl = storageService.store("avatars", filename, content);

        String previousUrl = user.getAvatarUrl();
        user.setAvatarUrl(newUrl);
        userRepository.save(user);

        if (previousUrl != null) {
            storageService.delete(previousUrl);
        }

        return getProfile(userId);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getUserById(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("New password must be different from the current password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        // every other device is signed out: their refresh tokens stop working (access tokens expire within minutes)
        refreshTokenService.revokeAll(userId);
    }
}

