package com.technova.campusdesk.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_tickets_status", columnList = "status"),
        @Index(name = "idx_tickets_requester", columnList = "requester_id"),
        @Index(name = "idx_tickets_technician", columnList = "technician_id")
})
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic locking: Hibernate adds "where version = ?" to every UPDATE and increments it. If two
     * transactions modify the same ticket, the second one fails (ObjectOptimisticLockingFailureException -> 409).
     * The default keeps rows that already existed valid when the column is added.
     */
    @Version
    @Column(name = "version", nullable = false, columnDefinition = "bigint default 0")
    private Long version;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", nullable = false, length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TicketStatus status = TicketStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tickets_requester"))
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_id", foreignKey = @ForeignKey(name = "fk_tickets_technician"))
    private User technician;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optional deadline requested by the user (date only). */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Set when the technician marks the ticket RESOLVED; cleared if the requester rejects the resolution. */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
