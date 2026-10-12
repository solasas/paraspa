package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.CreateSkillRequest;
import com.sashank.paraspa.dto.response.SkillResponse;
import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.enums.SkillCategory;
import com.sashank.paraspa.exception.AlreadyExistsException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.SkillRepository;
import com.sashank.paraspa.service.SkillService;
import com.sashank.paraspa.util.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SkillServiceImpl implements SkillService {

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    public SkillResponse createSkill(CreateSkillRequest request) {
        if (skillRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistsException("Skill with name '" + request.getName() + "' already exists");
        }

        Skill skill = Skill.builder()
                .name(request.getName())
                .category(request.getCategory())
                .description(request.getDescription())
                .build();

        skill = skillRepository.save(skill);
        return dtoMapper.toSkillResponse(skill);
    }

    @Override
    public List<SkillResponse> getAllSkills() {
        return skillRepository.findAll().stream()
                .map(dtoMapper::toSkillResponse)
                .toList();
    }

    @Override
    public List<SkillResponse> getSkillsByCategory(SkillCategory category) {
        return skillRepository.findByCategory(category).stream()
                .map(dtoMapper::toSkillResponse)
                .toList();
    }

    @Override
    public SkillResponse getSkillById(Long id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Skill not found"));
        return dtoMapper.toSkillResponse(skill);
    }
}

