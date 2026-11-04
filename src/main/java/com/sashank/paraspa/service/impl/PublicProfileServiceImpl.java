package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.PublicProfileResponse;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.SkillType;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.FollowRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.repository.UserSkillRepository;
import com.sashank.paraspa.service.PublicProfileService;
import com.sashank.paraspa.service.RatingService;
import com.sashank.paraspa.util.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PublicProfileServiceImpl implements PublicProfileService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private RatingService ratingService;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    public PublicProfileResponse getPublicProfile(Long viewerId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return PublicProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .bio(user.getBio())
                .city(user.getCity())
                .avatarUrl(user.getAvatarUrl())
                .ratingSummary(ratingService.getRatingSummary(userId))
                .teachSkills(dtoMapper.toUserSkillResponseList(
                        userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH)))
                .learnSkills(dtoMapper.toUserSkillResponseList(
                        userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN)))
                .followersCount(followRepository.countByFollowingId(userId))
                .followingCount(followRepository.countByFollowerId(userId))
                .followedByMe(followRepository.existsByFollowerIdAndFollowingId(viewerId, userId))
                .build();
    }
}
