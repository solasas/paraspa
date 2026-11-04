package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.entity.UserSkill;
import com.sashank.paraspa.enums.SkillLevel;
import com.sashank.paraspa.enums.SkillType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {
    // skill is EAGER; the entity graph makes it load in this same statement instead of one extra query per skill
    @EntityGraph(attributePaths = "skill")
    List<UserSkill> findByUserIdAndType(Long userId, SkillType type);
    List<UserSkill> findByUserId(Long userId);
    boolean existsByUserIdAndSkillIdAndType(Long userId, Long skillId, SkillType type);
    List<UserSkill> findBySkillInAndType(List<Skill> skills, SkillType type);
    List<UserSkill> findByTypeAndLevel(SkillType type, SkillLevel level);

    /**
     * Every user-skill row of the given type for the given skills, with the user and the skill loaded in the same
     * statement. Lets matching find all relevant people with one query instead of asking per user.
     * Callers must not pass an empty collection (an empty IN list is not valid SQL on every database).
     */
    @Query("SELECT us FROM UserSkill us JOIN FETCH us.user JOIN FETCH us.skill "
            + "WHERE us.skill.id IN :skillIds AND us.type = :type")
    List<UserSkill> findWithUserBySkillIdInAndType(@Param("skillIds") Collection<Long> skillIds,
                                                    @Param("type") SkillType type);
}

