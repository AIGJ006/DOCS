package com.team.blog.support;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 회원 테스트 데이터(JdbcTemplate으로 {@code member} + {@code auth_identity}를 직접 넣는다).
 *
 * <pre>{@code
 * long id = members().member().handle("kim755030").nickname("김민서").emailVerified(false).create();
 * long admin = members().member().role("ADMIN").create();
 * long gone = members().member().status("WITHDRAWN").create();   // withdrawn_at 채움 (ck_member_withdrawn)
 * members().suspend(id, Instant.now().plus(Duration.ofDays(7)), "스팸");
 * }</pre>
 *
 * 51 V1의 CHECK(`ck_member_handle`·`ck_member_nickname`·`ck_member_withdrawn`·`ck_member_deleted`·
 * `ck_auth_password`·`ck_auth_local_email`)를 만족하는 값을 만든다.
 */
public final class MemberFixtures {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(4);

    /** 기본 비밀번호 (가입 규칙을 통과하는 값). */
    public static final String DEFAULT_PASSWORD = "Blog#2026a";

    private final JdbcTemplate jdbc;

    public MemberFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Builder member() {
        return new Builder();
    }

    /**
     * 정지를 건다: {@code member_suspension} 행 추가 + {@code member.status = 'SUSPENDED'}. endsAt null =
     * 영구.
     */
    public long suspend(long memberId, Instant endsAt, String reason) {
        Instant now = Instant.now();
        Instant startedAt =
                endsAt != null && !endsAt.isAfter(now) ? endsAt.minus(Duration.ofDays(1)) : now;
        long admin = suspender();
        jdbc.update("UPDATE member SET status = 'SUSPENDED' WHERE id = ?", memberId);
        return jdbc.queryForObject(
                "INSERT INTO member_suspension (member_id, reason, started_at, ends_at, suspended_by)"
                        + " VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                memberId,
                reason,
                Timestamp.from(startedAt),
                endsAt == null ? null : Timestamp.from(endsAt),
                admin);
    }

    private long suspender() {
        Long existing =
                jdbc.query(
                        "SELECT id FROM member WHERE role = 'ADMIN' AND status = 'ACTIVE' ORDER BY id LIMIT 1",
                        rs -> rs.next() ? rs.getLong(1) : null);
        return existing != null ? existing : member().role("ADMIN").create();
    }

    public final class Builder {
        private final int n = SEQ.incrementAndGet();
        private String handle;
        private String nickname;
        private String provider = "LOCAL";
        private String providerUserId;
        private String email;
        private String status = "ACTIVE";
        private String role = "USER";
        private boolean emailVerified = true;
        private String password = DEFAULT_PASSWORD;
        private boolean deleted;
        private String defaultVisibility = "PUBLIC";
        private boolean lastActiveVisible = true;
        private Instant lastActiveAt;

        public Builder handle(String handle) {
            this.handle = handle;
            return this;
        }

        public Builder nickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        /** LOCAL·GOOGLE·GITHUB. */
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder providerUserId(String providerUserId) {
            this.providerUserId = providerUserId;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        /** ACTIVE·SUSPENDED·WITHDRAWN. WITHDRAWN이면 withdrawn_at을 채운다. */
        public Builder status(String status) {
            this.status = status;
            return this;
        }

        /** USER·ADMIN. */
        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder emailVerified(boolean emailVerified) {
            this.emailVerified = emailVerified;
            return this;
        }

        /** LOCAL 비밀번호 원문 (BCrypt로 저장). */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /** 익명 처리(015) 상태: status WITHDRAWN + deleted_at + 닉네임 NULL. */
        public Builder deleted() {
            this.deleted = true;
            this.status = "WITHDRAWN";
            return this;
        }

        public Builder defaultVisibility(String defaultVisibility) {
            this.defaultVisibility = defaultVisibility;
            return this;
        }

        public Builder lastActive(Instant lastActiveAt, boolean visible) {
            this.lastActiveAt = lastActiveAt;
            this.lastActiveVisible = visible;
            return this;
        }

        /** 회원을 만들고 회원 번호를 돌려준다. */
        public long create() {
            String prefix =
                    switch (provider) {
                        case "GOOGLE" -> "go-";
                        case "GITHUB" -> "gi-";
                        default -> "";
                    };
            String h = handle != null ? handle : prefix + "member" + n;
            String nick = deleted ? null : (nickname != null ? nickname : "회원" + n);
            Instant now = Instant.now();
            Long memberId =
                    jdbc.queryForObject(
                            "INSERT INTO member (handle, nickname, role, status, default_visibility,"
                                    + " withdrawn_at, last_active_at, last_active_visible, deleted_at)"
                                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                            Long.class,
                            h,
                            nick,
                            role,
                            status,
                            defaultVisibility,
                            "WITHDRAWN".equals(status) ? Timestamp.from(now) : null,
                            lastActiveAt == null ? null : Timestamp.from(lastActiveAt),
                            lastActiveVisible,
                            deleted ? Timestamp.from(now) : null);
            boolean local = "LOCAL".equals(provider);
            String mail = email != null ? email.trim().toLowerCase(Locale.ROOT) : null;
            if (local && mail == null) {
                mail = "member" + n + "@example.com";
            }
            String pid = local ? mail : (providerUserId != null ? providerUserId : "uid-" + n);
            jdbc.update(
                    "INSERT INTO auth_identity (member_id, provider, provider_user_id, email,"
                            + " password_hash, email_verified_at) VALUES (?, ?, ?, ?, ?, ?)",
                    memberId,
                    provider,
                    pid,
                    mail,
                    local ? BCRYPT.encode(password) : null,
                    emailVerified ? Timestamp.from(now) : null);
            return memberId;
        }
    }
}
