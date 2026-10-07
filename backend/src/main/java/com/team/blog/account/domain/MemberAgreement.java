package com.team.blog.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * 회원 동의 ({@code member_agreement}, data-model §2-3, 51 §2 — 4개 컬럼). PK {@code (member_id, type)},
 * 종류마다 한 행이며 재동의는 같은 행의 버전·시각을 바꾼다({@code MemberAgreementRepository#upsert}). 탈퇴 익명 처리 뒤에도 남는다.
 */
@Entity
@Table(name = "member_agreement")
@IdClass(MemberAgreement.Key.class)
public class MemberAgreement {

    @Id
    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20, updatable = false)
    private AgreementType type;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(name = "agreed_at", nullable = false)
    private Instant agreedAt;

    protected MemberAgreement() {}

    private MemberAgreement(long memberId, AgreementType type, String version, Instant agreedAt) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException(
                    "동의 버전은 비어 있을 수 없습니다"); // ck_member_agreement_version
        }
        this.memberId = memberId;
        this.type = Objects.requireNonNull(type, "type");
        this.version = version;
        this.agreedAt = Objects.requireNonNull(agreedAt, "agreedAt");
    }

    public static MemberAgreement of(
            long memberId, AgreementType type, String version, Instant agreedAt) {
        return new MemberAgreement(memberId, type, version, agreedAt);
    }

    public Long getMemberId() {
        return memberId;
    }

    public AgreementType getType() {
        return type;
    }

    public String getVersion() {
        return version;
    }

    public Instant getAgreedAt() {
        return agreedAt;
    }

    /** 복합 키 {@code (member_id, type)}. */
    public static class Key implements Serializable {

        @Serial private static final long serialVersionUID = 1L;

        private Long memberId;
        private AgreementType type;

        protected Key() {}

        public Key(Long memberId, AgreementType type) {
            this.memberId = memberId;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other
                    && Objects.equals(memberId, other.memberId)
                    && type == other.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(memberId, type);
        }
    }
}
