package com.sashank.paraspa.entity;

import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import org.junit.jupiter.api.Test;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the entity mapping against the schema created by migrations V2 and V6. */
class CommentMappingTest {

    @Test
    void comment_mapsToTheCommentsTable() {
        assertThat(Comment.class.getAnnotation(Table.class).name()).isEqualTo("comments");
    }

    @Test
    void comment_parentIsAnOptionalSelfReference() throws Exception {
        var parent = Comment.class.getDeclaredField("parent");

        assertThat(parent.getType()).isEqualTo(Comment.class);
        assertThat(parent.getAnnotation(ManyToOne.class).fetch()).isEqualTo(FetchType.LAZY);
        assertThat(parent.getAnnotation(JoinColumn.class).name()).isEqualTo("parent_id");
        assertThat(parent.getAnnotation(JoinColumn.class).nullable()).isTrue();   // null = top-level comment
    }

    @Test
    void postLike_isUniquePerPostAndUser() {
        String[] columns = Arrays.stream(PostLike.class.getAnnotation(Table.class).uniqueConstraints())
                .filter(c -> c.name().equals("uk_post_likes_post_user"))
                .findFirst().orElseThrow().columnNames();

        assertThat(columns).containsExactly("post_id", "user_id");
    }

    @Test
    void commentAndPostLike_toStringAndHashCode_shouldNotLoadOrRecurseThroughThePost() {
        Post post = Post.builder().id(1L).author(User.builder().id(1L).build()).build();
        Comment parent = Comment.builder().id(1L).post(post).content("p").build();
        Comment reply = Comment.builder().id(2L).post(post).parent(parent).content("r").build();

        assertThat(reply.toString()).contains("id=2");
        assertThat(reply.hashCode()).isEqualTo(reply.hashCode());
        assertThat(PostLike.builder().id(1L).post(post).build().toString()).contains("id=1");
    }
}
