package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.Availability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AvailabilityRepository extends JpaRepository<Availability, Long> {
    List<Availability> findByUserIdOrderByDayOfWeekAscStartTimeAsc(Long userId);
    Optional<Availability> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserId(Long userId);
}
