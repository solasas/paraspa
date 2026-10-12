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
import com.sashank.paraspa.service.UserService;
import com.sashank.paraspa.util.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
                .teachSkills(dtoMapper.toUserSkillResponseList(teachSkills))
                .learnSkills(dtoMapper.toUserSkillResponseList(learnSkills))
                .averageRating(avgRating != null ? avgRating : 0.0)
                .totalRatings(totalRatings)
                .build();
    }

    @Override
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

        user = userRepository.save(user);

        return getProfile(user.getId());
    }

    @Override
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
    }
}

