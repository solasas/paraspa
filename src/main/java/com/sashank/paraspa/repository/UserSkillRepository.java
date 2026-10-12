package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.entity.UserSkill;
import com.sashank.paraspa.enums.SkillLevel;
import com.sashank.paraspa.enums.SkillType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {
    List<UserSkill> findByUserIdAndType(Long userId, SkillType type);
    List<UserSkill> findByUserId(Long userId);
    boolean existsByUserIdAndSkillIdAndType(Long userId, Long skillId, SkillType type);
    List<UserSkill> findBySkillInAndType(List<Skill> skills, SkillType type);
    List<UserSkill> findByTypeAndLevel(SkillType type, SkillLevel level);
}

