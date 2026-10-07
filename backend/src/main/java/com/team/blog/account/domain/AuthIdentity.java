package com.team.blog.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * 로그인 수단 ({@code auth_identity}, data-model §2-2, 51 §2 — 9개 컬럼). 회원당 1개({@code
 * uq_auth_identity_member}).
 *
 * <p>LOCAL은 {@code provider_user_id = email}(소문자)이고 BCrypt 비밀번호가 있다({@code ck_auth_local_email},
 * {@code ck_auth_password}). {@code emailVerifiedAt}이 NULL이면 인증 전(42 P-6). 회원은 번호로만 가리킨다(모듈 경계).
 */
@Entity
@Table(name = "auth_identity")
public class AuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Provider provider;

    @Column(name = "provider_user_id", nullable = false, length = 255, updatable = false)
    private String providerUserId;

    @Column(length = 255)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected AuthIdentity() {}

    private AuthIdentity(
            long memberId,
            Provider provider,
            String providerUserId,
            String email,
            String passwordHash,
            Instant emailVerifiedAt,
            Instant now) {
        this.memberId = memberId;
        this.provider = Objects.requireNonNull(provider, "provider");
        this.providerUserId = Objects.requireNonNull(providerUserId, "providerUserId");
        this.email = email;
        this.passwordHash = passwordHash;
        this.emailVerifiedAt = emailVerifiedAt;
        this.createdAt = now;
    }

    /** 이메일 가입. 이메일은 소문자로 정규화해 {@code provider_user_id}에도 쓴다. 인증 전으로 시작한다. */
    public static AuthIdentity local(
            long memberId, String email, String passwordHash, Instant now) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return new AuthIdentity(
                memberId,
                Provider.LOCAL,
                normalized,
                normalized,
                Objects.requireNonNull(passwordHash, "passwordHash"),
                null,
                now);
    }

    /** 소셜 가입. 제공자가 확인한 이메일이면 가입 시각을 인증 일자로 둔다. */
    public static AuthIdentity social(
            long memberId,
            Provider provider,
            String providerUserId,
            String email,
            boolean emailVerified,
            Instant now) {
        if (provider == Provider.LOCAL) {
            throw new IllegalArgumentException("소셜 제공자가 아닙니다");
        }
        String normalized = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        return new AuthIdentity(
                memberId,
                provider,
                providerUserId,
                normalized,
                null,
                emailVerified ? now : null,
                now);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Provider getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    /** 이메일 인증 완료(인증 링크 확인). 이미 인증됐으면 그대로 둔다 — 되돌아가는 전이는 없다(data-model §4-2). */
    public void verifyEmail(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = Objects.requireNonNull(now, "now");
        }
    }

    /**
     * 로그인 성공 기록. 갱신하기 <b>전</b> 값을 돌려준다 — 세션 {@code previousLoginAt}으로 쓴다(FR-057, 07 §6). 첫 로그인이면
     * null.
     */
    public Instant recordLogin(Instant now) {
        Instant previous = lastLoginAt;
        lastLoginAt = Objects.requireNonNull(now, "now");
        return previous;
    }
}
