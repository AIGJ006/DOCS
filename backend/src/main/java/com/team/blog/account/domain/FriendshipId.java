package com.team.blog.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** 친구 관계 키 — 항상 {@code memberAId = LEAST}, {@code memberBId = GREATEST} (data-model §2-5). */
@Embeddable
public class FriendshipId implements Serializable {

    @Column(name = "member_a_id", nullable = false)
    private long memberAId;

    @Column(name = "member_b_id", nullable = false)
    private long memberBId;

    protected FriendshipId() {}

    private FriendshipId(long memberAId, long memberBId) {
        this.memberAId = memberAId;
        this.memberBId = memberBId;
    }

    /** 두 회원의 키. 같은 회원이면 {@code IllegalArgumentException}. */
    public static FriendshipId of(long one, long other) {
        if (one == other) {
            throw new IllegalArgumentException("자기 자신과의 관계는 없습니다");
        }
        return new FriendshipId(Math.min(one, other), Math.max(one, other));
    }

    public long memberAId() {
        return memberAId;
    }

    public long memberBId() {
        return memberBId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FriendshipId that
                && memberAId == that.memberAId
                && memberBId == that.memberBId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberAId, memberBId);
    }
}
