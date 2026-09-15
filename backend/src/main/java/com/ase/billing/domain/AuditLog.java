package com.ase.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * What was done to a financial record and by whom.
 *
 * Written before a destructive change, not after, so the trail survives even if
 * the row it describes is gone.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(length = 60)
    private String username;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
