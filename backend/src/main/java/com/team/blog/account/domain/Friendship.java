package com.team.blog.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 친구 관계 (data-model §2-5, FR-054~056). 쌍마다 1행({@code friendship_pkey}). 요청·수락은 경쟁을 피하려고 {@code
 * FriendshipRepository}의 {@code INSERT … ON CONFLICT} 한 문장으로 하고, 이 엔티티는 읽기 표현이다.
 */
@Entity
@Table(name = "friendship")
public class Friendship {

    @EmbeddedId private FriendshipId id;

    @Column(name = "requested_by", nullable = false)
    private long requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FriendshipStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected Friendship() {}

    public Friendship(
            FriendshipId id,
            long requestedBy,
            FriendshipStatus status,
            Instant createdAt,
            Instant acceptedAt) {
        this.id = id;
        this.requestedBy = requestedBy;
        this.status = status;
        this.createdAt = createdAt;
        this.acceptedAt = acceptedAt;
    }

    public FriendshipId getId() {
        return id;
    }

    public long getRequestedBy() {
        return requestedBy;
    }

    public FriendshipStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public boolean isAccepted() {
        return status == FriendshipStatus.ACCEPTED;
    }
}
