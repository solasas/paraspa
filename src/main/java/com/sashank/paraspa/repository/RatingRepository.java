package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {
    List<Rating> findByRatedToId(Long userId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.ratedTo.id = :userId AND r.stars = :stars")
    long countByUserAndStars(@Param("userId") Long userId, @Param("stars") Integer stars);

    @Query("SELECT AVG(r.stars) FROM Rating r WHERE r.ratedTo.id = :userId")
    Double getAverageRatingByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.ratedTo.id = :userId")
    long countByRatedToId(@Param("userId") Long userId);

    Optional<Rating> findBySessionIdAndRatedById(Long sessionId, Long userId);
}

