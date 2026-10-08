package com.technova.campusdesk.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Append-only audit trail of ticket state changes. It has no update or delete endpoint. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "status_history", indexes = @Index(name = "idx_status_history_ticket", columnList = "ticket_id"))
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, foreignKey = @ForeignKey(name = "fk_history_ticket"))
    private Ticket ticket;

    /** Null only for the first entry, created together with the ticket. */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private TicketStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private TicketStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_id", nullable = false, foreignKey = @ForeignKey(name = "fk_history_user"))
    private User changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "note", length = 255)
    private String note;

    @PrePersist
    void onCreate() {
        changedAt = Instant.now();
    }
}
