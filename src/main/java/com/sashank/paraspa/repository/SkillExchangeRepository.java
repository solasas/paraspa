package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.SkillExchange;
import com.sashank.paraspa.enums.ExchangeStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SkillExchangeRepository extends JpaRepository<SkillExchange, Long> {
    List<SkillExchange> findByRequesterId(Long userId);
    List<SkillExchange> findByReceiverId(Long userId);
    List<SkillExchange> findByRequesterIdOrReceiverId(Long requesterId, Long receiverId);

    List<SkillExchange> findByRequesterIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
    List<SkillExchange> findByReceiverIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
    List<SkillExchange> findByRequesterIdOrReceiverIdOrderByCreatedAtDescIdDesc(
            Long requesterId, Long receiverId, Pageable pageable);
}

