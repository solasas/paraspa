package com.sashank.skillswap.service.impl;

import com.sashank.skillswap.dto.response.UserResponse;
import com.sashank.skillswap.entity.Skill;
import com.sashank.skillswap.entity.User;
import com.sashank.skillswap.entity.UserSkill;
import com.sashank.skillswap.enums.SkillLevel;
import com.sashank.skillswap.enums.SkillType;
import com.sashank.skillswap.repository.AvailabilityRepository;
import com.sashank.skillswap.repository.RatingRepository;
import com.sashank.skillswap.repository.SkillRepository;
import com.sashank.skillswap.repository.UserRepository;
import com.sashank.skillswap.repository.UserSkillRepository;
import com.sashank.skillswap.service.UserSearchService;
import com.sashank.skillswap.util.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserSearchServiceImpl implements UserSearchService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private AvailabilityRepository availabilityRepository;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    public List<UserResponse> searchUsers(Long currentUserId, String skillName, String city,
                                           SkillLevel level, Double minRating, Boolean availableOnly) {
        List<User> candidates = findCandidates(skillName, level);

        return candidates.stream()
                .filter(user -> !user.getId().equals(currentUserId))
                .filter(user -> matchesCity(user, city))
                .filter(user -> availableOnly == null || !availableOnly || availabilityRepository.existsByUserId(user.getId()))
                .map(this::toUserResponseWithRatings)
                .filter(userResponse -> minRating == null || userResponse.getAverageRating() >= minRating)
                .collect(Collectors.toList());
    }

    private List<User> findCandidates(String skillName, SkillLevel level) {
        if (skillName != null && !skillName.isBlank()) {
            List<Skill> matchingSkills = skillRepository.findByNameContainingIgnoreCase(skillName);
            return userSkillRepository.findBySkillInAndType(matchingSkills, SkillType.TEACH).stream()
                    .filter(us -> level == null || us.getLevel() == level)
                    .map(UserSkill::getUser)
                    .distinct()
                    .collect(Collectors.toList());
        }

        if (level != null) {
            return userSkillRepository.findByTypeAndLevel(SkillType.TEACH, level).stream()
                    .map(UserSkill::getUser)
                    .distinct()
                    .collect(Collectors.toList());
        }

        return userRepository.findAll();
    }

    private boolean matchesCity(User user, String city) {
        if (city == null || city.isBlank()) {
            return true;
        }
        return user.getCity() != null && user.getCity().toLowerCase().contains(city.toLowerCase());
    }

    private UserResponse toUserResponseWithRatings(User user) {
        Double avgRating = ratingRepository.getAverageRatingByUserId(user.getId());
        Long totalRatings = ratingRepository.countByRatedToId(user.getId());
        UserResponse response = dtoMapper.toUserResponseWithRatings(user, avgRating != null ? avgRating : 0.0, totalRatings);
        if (response != null) {
            response.setEmail(null);
        }
        return response;
    }
}
