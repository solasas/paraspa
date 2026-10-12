package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.AddUserSkillRequest;
import com.sashank.paraspa.dto.response.UserSkillResponse;
import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.entity.UserSkill;
import com.sashank.paraspa.exception.AlreadyExistsException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.SkillRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.repository.UserSkillRepository;
import com.sashank.paraspa.service.UserSkillService;
import com.sashank.paraspa.util.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserSkillServiceImpl implements UserSkillService {

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    public UserSkillResponse addUserSkill(Long userId, AddUserSkillRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Skill skill = skillRepository.findById(request.getSkillId())
                .orElseThrow(() -> new ResourceNotFoundException("Skill not found"));

        if (userSkillRepository.existsByUserIdAndSkillIdAndType(userId, skill.getId(), request.getType())) {
            throw new AlreadyExistsException("Skill already added to your " + request.getType().name().toLowerCase() + " list");
        }

        UserSkill userSkill = UserSkill.builder()
                .user(user)
                .skill(skill)
                .type(request.getType())
                .level(request.getLevel())
                .build();

        userSkill = userSkillRepository.save(userSkill);
        return dtoMapper.toUserSkillResponse(userSkill);
    }

    @Override
    public List<UserSkillResponse> getUserSkills(Long userId) {
        List<UserSkill> skills = userSkillRepository.findByUserId(userId);
        return dtoMapper.toUserSkillResponseList(skills);
    }

    @Override
    public void removeUserSkill(Long userId, Long userSkillId) {
        UserSkill userSkill = userSkillRepository.findById(userSkillId)
                .orElseThrow(() -> new ResourceNotFoundException("User skill not found"));

        if (!userSkill.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("User skill not found");
        }

        userSkillRepository.deleteById(userSkillId);
    }
}

