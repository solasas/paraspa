package com.sashank.paraspa.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the entity mapping against the schema created by migration V5. */
class PostMappingTest {

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        return type.getDeclaredField(name);
    }

    @Test
    void media_isRemovedTogetherWithThePostAndKeptInPositionOrder() throws Exception {
        Field media = field(Post.class, "media");
        OneToMany mapping = media.getAnnotation(OneToMany.class);

        assertThat(mapping.mappedBy()).isEqualTo("post");
        assertThat(mapping.cascade()).contains(CascadeType.ALL);   // delete the post => delete its media rows
        assertThat(mapping.orphanRemoval()).isTrue();
        assertThat(media.getAnnotation(OrderBy.class).value()).isEqualTo("position ASC");
    }

    @Test
    void skills_areManyToManyThroughPostSkills() throws Exception {
        Field skills = field(Post.class, "skills");
        JoinTable table = skills.getAnnotation(JoinTable.class);

        assertThat(skills.getAnnotation(ManyToMany.class)).isNotNull();
        assertThat(table.name()).isEqualTo("post_skills");
        assertThat(table.joinColumns()[0].name()).isEqualTo("post_id");
        assertThat(table.inverseJoinColumns()[0].name()).isEqualTo("skill_id");
    }

    @Test
    void postMedia_hasOneRowPerPostAndPosition() {
        String[] columns = Arrays.stream(PostMedia.class.getAnnotation(Table.class).uniqueConstraints())
                .filter(c -> c.name().equals("uk_post_media_post_position"))
                .findFirst().orElseThrow().columnNames();

        assertThat(columns).containsExactly("post_id", "position");
    }

    @Test
    void counters_defaultToZero() {
        Post post = Post.builder().build();

        assertThat(post.getLikeCount()).isZero();
        assertThat(post.getCommentCount()).isZero();
        assertThat(post.getSkills()).isEmpty();
        assertThat(post.getMedia()).isEmpty();
    }
}
