package com.sashank.paraspa.util;

import com.sashank.paraspa.dto.response.PostResponse;
import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.entity.PostMedia;
import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.entity.User;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DtoMapperPostTest {

    private final DtoMapper mapper = new DtoMapper();

    @Test
    void toPostResponse_shouldUseTheStoredCountersTagsAndMediaInOrder() {
        Post post = Post.builder().id(100L)
                .author(User.builder().id(1L).name("Asha").email("asha@example.com").build())
                .content("hello")
                .likeCount(7).commentCount(3)
                .createdAt(LocalDateTime.of(2026, 11, 4, 12, 0))
                .build();
        post.getSkills().add(Skill.builder().id(2L).name("Piano").build());
        post.getSkills().add(Skill.builder().id(1L).name("Guitar").build());
        post.getMedia().add(PostMedia.builder().post(post).url("/files/posts/a.png").position(0).build());
        post.getMedia().add(PostMedia.builder().post(post).url("/files/posts/b.png").position(1).build());

        PostResponse response = mapper.toPostResponse(post, true);

        assertThat(response.getLikeCount()).isEqualTo(7);
        assertThat(response.getCommentCount()).isEqualTo(3);
        assertThat(response.isLikedByMe()).isTrue();
        assertThat(response.getSkills()).extracting("name").containsExactly("Piano", "Guitar");
        assertThat(response.getMedia()).extracting("url").containsExactly("/files/posts/a.png", "/files/posts/b.png");
        assertThat(response.getMedia()).extracting("position").containsExactly(0, 1);
        assertThat(response.getContent()).isEqualTo("hello");
    }

    @Test
    void toPostResponse_shouldNeverExposeTheAuthorsEmail() {
        Post post = Post.builder().id(1L).author(User.builder().id(1L).email("secret@example.com").build()).build();

        assertThat(mapper.toPostResponse(post, false).getAuthor().getEmail()).isNull();
    }

    @Test
    void toPostResponse_shouldHandleAnImageOnlyPostWithNoTags() {
        Post post = Post.builder().id(1L).author(User.builder().id(1L).build()).content(null).build();

        PostResponse response = mapper.toPostResponse(post, false);

        assertThat(response.getContent()).isNull();
        assertThat(response.getSkills()).isEmpty();
        assertThat(response.getMedia()).isEmpty();
    }

    @Test
    void postEntityToString_andHashCode_shouldNotRecurseThroughMedia() {
        Post post = Post.builder().id(1L).author(User.builder().id(1L).build()).build();
        post.getMedia().add(PostMedia.builder().post(post).url("/files/posts/a.png").position(0).build());

        // Post <-> PostMedia point at each other; Lombok would loop forever without the exclusions
        assertThat(post.toString()).contains("id=1");
        assertThat(post.hashCode()).isEqualTo(post.hashCode());
        assertThat(post.getMedia().get(0).toString()).contains("a.png");
    }

    @Test
    void toCommentResponse_shouldCarryParentIdAndHideTheAuthorsEmail() {
        com.sashank.paraspa.entity.Post post = Post.builder().id(100L).author(User.builder().id(9L).build()).build();
        com.sashank.paraspa.entity.Comment parent = com.sashank.paraspa.entity.Comment.builder().id(10L).post(post).build();
        com.sashank.paraspa.entity.Comment reply = com.sashank.paraspa.entity.Comment.builder().id(11L).post(post).parent(parent)
                .author(User.builder().id(1L).email("secret@example.com").build()).content("hi").build();

        com.sashank.paraspa.dto.response.CommentResponse response = mapper.toCommentResponse(reply);

        assertThat(response.getId()).isEqualTo(11L);
        assertThat(response.getPostId()).isEqualTo(100L);
        assertThat(response.getParentId()).isEqualTo(10L);
        assertThat(response.getAuthor().getEmail()).isNull();
        assertThat(response.getReplies()).isEmpty();
    }

    @Test
    void toCommentResponse_topLevelCommentShouldHaveNoParentId() {
        com.sashank.paraspa.entity.Post post = Post.builder().id(100L).author(User.builder().id(9L).build()).build();
        com.sashank.paraspa.entity.Comment top = com.sashank.paraspa.entity.Comment.builder().id(10L).post(post)
                .author(User.builder().id(1L).build()).content("hi").build();

        assertThat(mapper.toCommentResponse(top).getParentId()).isNull();
    }
}
