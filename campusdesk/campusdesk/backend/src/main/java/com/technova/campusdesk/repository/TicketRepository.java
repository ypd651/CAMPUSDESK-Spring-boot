package com.technova.campusdesk.repository;

import com.technova.campusdesk.entity.Ticket;
import com.technova.campusdesk.entity.TicketStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    long countByRequesterIdAndStatusIn(Long requesterId, Collection<TicketStatus> statuses);

    /** Ids of tickets in the given status whose resolution is older than the cutoff (auto-close job). */
    @Query("select t.id from Ticket t where t.status = :status and t.resolvedAt < :cutoff")
    List<Long> findIdsByStatusAndResolvedAtBefore(@Param("status") TicketStatus status, @Param("cutoff") Instant cutoff);

    /** Rows of [category, count] for the advanced statistics. */
    @Query("select t.category, count(t) from Ticket t group by t.category")
    List<Object[]> countByCategory();

    /** Rows of [technicianId, fullName, count] with every ticket ever assigned to each technician. */
    @Query("select u.id, u.fullName, count(t) from Ticket t join t.technician u group by u.id, u.fullName order by u.fullName")
    List<Object[]> countAssignedByTechnician();

    /** Rows of [technicianId, createdAt, resolvedAt] for tickets that reached RESOLVED. */
    @Query("select u.id, t.createdAt, t.resolvedAt from Ticket t join t.technician u where t.resolvedAt is not null")
    List<Object[]> findResolutionTimes();
}
