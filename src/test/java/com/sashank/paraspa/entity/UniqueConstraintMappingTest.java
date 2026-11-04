package com.sashank.paraspa.entity;

import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the entity mapping against the unique constraints created by migration V3. */
class UniqueConstraintMappingTest {

    private static String[] uniqueColumns(Class<?> entity, String constraintName) {
        return Arrays.stream(entity.getAnnotation(Table.class).uniqueConstraints())
                .filter(c -> c.name().equals(constraintName))
                .map(UniqueConstraint::columnNames)
                .findFirst()
                .orElseThrow(() -> new AssertionError(entity.getSimpleName() + " lacks " + constraintName));
    }

    @Test
    void userSkill_isUniquePerUserSkillAndType() {
        assertThat(uniqueColumns(UserSkill.class, "uk_user_skills_user_skill_type"))
                .containsExactly("user_id", "skill_id", "type");
    }

    @Test
    void rating_isUniquePerSessionAndRater() {
        assertThat(uniqueColumns(Rating.class, "uk_ratings_session_rated_by"))
                .containsExactly("session_id", "rated_by_id");
    }
}
