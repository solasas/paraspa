package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.MatchResponse;
import com.sashank.paraspa.dto.response.SkillResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.entity.UserSkill;
import com.sashank.paraspa.enums.SkillType;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.repository.UserSkillRepository;
import com.sashank.paraspa.service.MatchService;
import com.sashank.paraspa.util.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.sashank.paraspa.service.BlockService;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class MatchServiceImpl implements MatchService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private BlockService blockService;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    public List<MatchResponse> findMatches(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Set<Long> myLearnSkillIds = skillIds(userSkillRepository.findByUserIdAndType(userId, SkillType.LEARN));
        Set<Long> myTeachSkillIds = skillIds(userSkillRepository.findByUserIdAndType(userId, SkillType.TEACH));

        // Only the rows that can produce a match are loaded, each kind in ONE query, whatever the number of users:
        // people who teach something I want to learn, and people who want to learn something I teach.
        Map<Long, List<UserSkill>> teachingWhatIWant = myLearnSkillIds.isEmpty() ? Map.of()
                : groupByUser(userSkillRepository.findWithUserBySkillIdInAndType(myLearnSkillIds, SkillType.TEACH));
        Map<Long, List<UserSkill>> learningWhatITeach = myTeachSkillIds.isEmpty() ? Map.of()
                : groupByUser(userSkillRepository.findWithUserBySkillIdInAndType(myTeachSkillIds, SkillType.LEARN));

        Set<Long> candidateIds = new TreeSet<>(teachingWhatIWant.keySet());
        candidateIds.addAll(learningWhatITeach.keySet());
        candidateIds.remove(userId);
        if (!candidateIds.isEmpty()) {
            candidateIds.removeAll(blockService.getHiddenUserIds(userId));   // blocked either way: never matched (one query)
        }

        return candidateIds.stream()
                .map(candidateId -> {
                    List<UserSkill> theyTeach = teachingWhatIWant.getOrDefault(candidateId, List.of());
                    List<UserSkill> theyLearn = learningWhatITeach.getOrDefault(candidateId, List.of());
                    User candidate = !theyTeach.isEmpty() ? theyTeach.get(0).getUser() : theyLearn.get(0).getUser();
                    return buildMatchResponse(candidate, theyTeach, theyLearn, myLearnSkillIds, myTeachSkillIds);
                })
                .filter(match -> !match.getTheyCanTeachMe().isEmpty() || !match.getICanTeachThem().isEmpty())
                // mutual matches first; the candidate ids were visited in ascending order and the sort is stable
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
        if (blockService.isBlockedEitherWay(userId, otherUserId)) {
            throw new ResourceNotFoundException("User not found");
        }

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

    private static Set<Long> skillIds(List<UserSkill> userSkills) {
        return userSkills.stream().map(us -> us.getSkill().getId()).collect(Collectors.toSet());
    }

    private static Map<Long, List<UserSkill>> groupByUser(List<UserSkill> userSkills) {
        return userSkills.stream().collect(Collectors.groupingBy(us -> us.getUser().getId()));
    }

    private MatchResponse buildMatchResponse(User otherUser, Set<Long> myLearnSkillIds, Set<Long> myTeachSkillIds) {
        return buildMatchResponse(otherUser,
                userSkillRepository.findByUserIdAndType(otherUser.getId(), SkillType.TEACH),
                userSkillRepository.findByUserIdAndType(otherUser.getId(), SkillType.LEARN),
                myLearnSkillIds, myTeachSkillIds);
    }

    private MatchResponse buildMatchResponse(User otherUser, List<UserSkill> theirTeachSkills,
                                             List<UserSkill> theirLearnSkills,
                                             Set<Long> myLearnSkillIds, Set<Long> myTeachSkillIds) {
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
