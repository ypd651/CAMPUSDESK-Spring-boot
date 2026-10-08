package com.technova.campusdesk.repository;

import com.technova.campusdesk.entity.StatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByTicketIdOrderByChangedAtAscIdAsc(Long ticketId);
}
