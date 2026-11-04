package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {
    List<PostComment> findByPostIdOrderByCreatedAtAsc(Long postId);

    Optional<PostComment> findByIdAndPostId(Long id, Long postId);

    long countByPostId(Long postId);

    void deleteByPostId(Long postId);
}
