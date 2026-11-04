package com.sashank.paraspa.entity;

import jakarta.persistence.Table;
import org.hibernate.annotations.Check;
import org.junit.jupiter.api.Test;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the entity mapping against the constraints created by migrations V2 and V4. */
class FollowMappingTest {

    @Test
    void follow_isUniquePerFollowerAndFollowing() {
        String[] columns = Arrays.stream(Follow.class.getAnnotation(Table.class).uniqueConstraints())
                .filter(c -> c.name().equals("uk_follows_follower_following"))
                .findFirst().orElseThrow().columnNames();

        assertThat(columns).containsExactly("follower_id", "following_id");
    }

    @Test
    void follow_forbidsFollowingYourself() {
        Check check = Follow.class.getAnnotation(Check.class);

        assertThat(check).isNotNull();
        assertThat(check.name()).isEqualTo("chk_follows_not_self");
        assertThat(check.constraints()).isEqualTo("follower_id <> following_id");
    }
}
