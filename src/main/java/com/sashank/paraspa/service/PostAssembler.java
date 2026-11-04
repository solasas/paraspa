package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.response.PostResponse;
import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.repository.PostLikeRepository;
import com.sashank.paraspa.util.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Set;

/**
 * Turns a page of posts into responses with a fixed number of queries: one for "which of these did the viewer
 * like"; authors come with the posts (JOIN FETCH) and media/skills are batch-loaded by Hibernate (@BatchSize).
 */
@Component
public class PostAssembler {

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private DtoMapper dtoMapper;

    public List<PostResponse> assemble(List<Post> posts, Long viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Set<Long> liked = Set.copyOf(postLikeRepository.findLikedPostIds(
                viewerId, posts.stream().map(Post::getId).toList()));
        return posts.stream()
                .map(post -> dtoMapper.toPostResponse(post, liked.contains(post.getId())))
                .toList();
    }
}
