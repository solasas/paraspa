package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.Report;
import com.sashank.paraspa.enums.ReportStatus;
import com.sashank.paraspa.enums.ReportTargetType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

    /** The moderators' queue, oldest first. Reporter and resolver come in the same statement. */
    @EntityGraph(attributePaths = {"reporter", "resolvedBy"})
    List<Report> findByStatusOrderByIdAsc(ReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "resolvedBy"})
    List<Report> findByStatusAndIdGreaterThanOrderByIdAsc(ReportStatus status, Long afterId, Pageable pageable);

    /** How many reports each of the given targets of one type has: rows of (targetId, count). */
    @Query("SELECT r.targetId, COUNT(r) FROM Report r WHERE r.targetType = :type AND r.targetId IN :ids GROUP BY r.targetId")
    List<Object[]> countByTarget(@Param("type") ReportTargetType type, @Param("ids") Collection<Long> ids);
}
