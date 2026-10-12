package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.RescheduleRequest;
import com.sashank.paraspa.enums.RescheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RescheduleRequestRepository extends JpaRepository<RescheduleRequest, Long> {
    List<RescheduleRequest> findBySessionId(Long sessionId);
    boolean existsBySessionIdAndStatus(Long sessionId, RescheduleStatus status);
}
