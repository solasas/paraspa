package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {
    List<Session> findByExchangeId(Long exchangeId);

    @Query("SELECT s FROM Session s WHERE (s.exchange.requester.id = :userId OR s.exchange.receiver.id = :userId) AND s.status = 'SCHEDULED' ORDER BY s.dateTime ASC")
    List<Session> findUpcomingSessionsByUserId(@Param("userId") Long userId);
}

