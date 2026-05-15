package com.company.leavems.balance.domain;

import com.company.leavems.leave.domain.LeaveType;
import com.company.leavems.user.domain.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "leave_balances", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "leave_type"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false)
    private LeaveType leaveType;

    @Min(0)
    @Column(nullable = false)
    private Integer remainingDays;

    @Version
    private Long version;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public LeaveBalance(User user, LeaveType leaveType, Integer remainingDays) {
        this.user = user;
        this.leaveType = leaveType;
        this.remainingDays = remainingDays;
    }

    public void debit(int days) {
        if (days < 0) throw new IllegalArgumentException("Days cannot be negative");
        if (remainingDays < days) throw new IllegalStateException("Insufficient leave balance");
        this.remainingDays -= days;
    }

    public void restore(int days) {
        if (days < 0) throw new IllegalArgumentException("Days cannot be negative");
        this.remainingDays += days;
    }
}