package com.team.blog.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원 정지 이력 ({@code member_suspension}, data-model §2-4 — 8개 컬럼). 열린 정지 = {@code lifted_at IS NULL}.
 * 회원당 열린 정지는 하나다(51 §4 E5 — Service가 지킨다). 정지 생성은 014(R-31), 001은 로그인 때 읽기와 기한이 지난 정지 해제만 한다.
 */
@Entity
@Table(name = "member_suspension")
public class MemberSuspension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "suspended_by", nullable = false, updatable = false)
    private Long suspendedBy;

    @Column(name = "lifted_at")
    private Instant liftedAt;

    @Column(name = "lifted_by")
    private Long liftedBy;

    protected MemberSuspension() {}

    /** 새 정지 (014 T052). {@code endsAt} null = 영구. */
    public static MemberSuspension start(
            long memberId, String reason, Instant startedAt, Instant endsAt, long suspendedBy) {
        MemberSuspension s = new MemberSuspension();
        s.memberId = memberId;
        s.reason = reason;
        s.startedAt = startedAt;
        s.endsAt = endsAt;
        s.suspendedBy = suspendedBy;
        return s;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    /** null = 영구 정지. */
    public Instant getEndsAt() {
        return endsAt;
    }

    public Long getSuspendedBy() {
        return suspendedBy;
    }

    public Instant getLiftedAt() {
        return liftedAt;
    }

    public Long getLiftedBy() {
        return liftedBy;
    }

    public boolean isOpen() {
        return liftedAt == null;
    }

    /** 기한이 있고 지났다. */
    public boolean isExpiredAt(Instant now) {
        return endsAt != null && !endsAt.isAfter(now);
    }

    /** 해제한다. 기한 만료 자동 해제는 {@code liftedBy = null} (R-23). */
    public void lift(Instant now, Long liftedBy) {
        this.liftedAt = now;
        this.liftedBy = liftedBy;
    }
}
