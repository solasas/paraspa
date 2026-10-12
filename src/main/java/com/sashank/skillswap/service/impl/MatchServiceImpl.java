package com.sashank.skillswap.service.impl;

import com.sashank.skillswap.dto.response.MatchResponse;
import com.sashank.skillswap.dto.response.SkillResponse;
import com.sashank.skillswap.dto.response.UserResponse;
import com.sashank.skillswap.entity.Skill;
import com.sashank.skillswap.entity.User;
import com.sashank.skillswap.entity.UserSkill;
import com.sashank.skillswap.enums.SkillType;
import com.sashank.skillswap.exception.ResourceNotFoundException;
import com.sashank.skillswap.repository.UserRepository;
import com.sashank.skillswap.repository.UserSkillRepository;
import com.sashank.skillswap.service.MatchService;
import com.sashank.skillswap.util.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MatchServiceImpl implements MatchService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    public List<MatchResponse> findMatches(Long userId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<UserSkill> myLearnSkills = userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN);
        List<UserSkill> myTeachSkills = userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH);

        Set<Long> myLearnSkillIds = myLearnSkills.stream()
                .map(us -> us.getSkill().getId())
                .collect(Collectors.toSet());

        Set<Long> myTeachSkillIds = myTeachSkills.stream()
                .map(us -> us.getSkill().getId())
                .collect(Collectors.toSet());

        List<User> allUsers = userRepository.findAll();

        return allUsers.stream()
                .filter(user -> !user.getId().equals(userId))
                .map(otherUser -> buildMatchResponse(otherUser, myLearnSkillIds, myTeachSkillIds))
                .filter(match -> !match.getTheyCanTeachMe().isEmpty() || !match.getICanTeachThem().isEmpty())
                .sorted((a, b) -> Boolean.compare(b.isMutual(), a.isMutual()))
                .collect(Collectors.toList());
    }

    @Override
    public List<MatchResponse> findMutualMatches(Long userId) {
        return findMatches(userId).stream()
                .filter(MatchResponse::isMutual)
                .collect(Collectors.toList());
    }

    @Override
    public MatchResponse checkMatch(Long userId, Long otherUserId) {
        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<UserSkill> myLearnSkills = userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN);
        List<UserSkill> myTeachSkills = userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH);

        Set<Long> myLearnSkillIds = myLearnSkills.stream()
                .map(us -> us.getSkill().getId())
                .collect(Collectors.toSet());

        Set<Long> myTeachSkillIds = myTeachSkills.stream()
                .map(us -> us.getSkill().getId())
                .collect(Collectors.toSet());

        return buildMatchResponse(otherUser, myLearnSkillIds, myTeachSkillIds);
    }

    private UserResponse withoutEmail(UserResponse user) {
        if (user != null) {
            user.setEmail(null);
        }
        return user;
    }

    private MatchResponse buildMatchResponse(User otherUser, Set<Long> myLearnSkillIds, Set<Long> myTeachSkillIds) {
        List<UserSkill> theirTeachSkills = userSkillRepository.findByUserIdAndType(otherUser.getId(), SkillType.TEACH);
        List<UserSkill> theirLearnSkills = userSkillRepository.findByUserIdAndType(otherUser.getId(), SkillType.LEARN);

        List<SkillResponse> theyCanTeachMe = theirTeachSkills.stream()
                .filter(us -> myLearnSkillIds.contains(us.getSkill().getId()))
                .map(us -> dtoMapper.toSkillResponse(us.getSkill()))
                .collect(Collectors.toList());

        List<SkillResponse> iCanTeachThem = theirLearnSkills.stream()
                .filter(us -> myTeachSkillIds.contains(us.getSkill().getId()))
                .map(us -> dtoMapper.toSkillResponse(us.getSkill()))
                .collect(Collectors.toList());

        boolean isMutual = !theyCanTeachMe.isEmpty() && !iCanTeachThem.isEmpty();

        return MatchResponse.builder()
                .user(withoutEmail(dtoMapper.toUserResponse(otherUser)))
                .theyCanTeachMe(theyCanTeachMe)
                .iCanTeachThem(iCanTeachThem)
                .isMutual(isMutual)
                .build();
    }
}

