package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    List<Post> findBySkillIdOrderByCreatedAtDescIdDesc(Long skillId, Pageable pageable);

    List<Post> findByAuthorIdOrderByCreatedAtDescIdDesc(Long authorId, Pageable pageable);

    List<Post> findByAuthorIdInOrderByCreatedAtDescIdDesc(Collection<Long> authorIds, Pageable pageable);

    List<Post> findByAuthorIdInAndSkillIdOrderByCreatedAtDescIdDesc(
            Collection<Long> authorIds, Long skillId, Pageable pageable);
}
