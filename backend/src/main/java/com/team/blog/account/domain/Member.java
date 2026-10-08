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
import java.util.Objects;

/**
 * 회원 ({@code member}, data-model §2-1, 51 §2 — 14개 컬럼).
 *
 * <p>{@code handle}은 가입 때 한 번 정해지고 바꾸는 메서드가 없다(FR-021). {@code defaultVisibility}는 문자열 컬럼 값 ({@code
 * PUBLIC}/{@code PRIVATE})으로 두고, 공개 범위 enum은 004 {@code post.domain.Visibility}가 소유한다. 형식 규칙은 서버
 * 정책과 DB CHECK({@code ck_member_handle}, {@code ck_member_nickname} 등)가 2중으로 지킨다.
 */
@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 39, updatable = false)
    private String handle;

    @Column(length = 10)
    private String nickname;

    @Column(name = "nickname_changed_at")
    private Instant nicknameChangedAt;

    @Column(length = 200)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "default_visibility", nullable = false, length = 20)
    private String defaultVisibility;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "last_active_visible", nullable = false)
    private boolean lastActiveVisible;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Member() {}

    private Member(String handle, String nickname, Instant now) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.nickname = Objects.requireNonNull(nickname, "nickname");
        this.role = Role.USER;
        this.status = MemberStatus.ACTIVE;
        this.defaultVisibility = "PUBLIC";
        this.createdAt = now;
        this.updatedAt = now;
        this.lastActiveVisible = true;
    }

    /** 새 회원(가입). 역할 USER, 상태 ACTIVE, 새 글 기본 공개 범위 PUBLIC. 닉네임 변경 일자는 비워 둔다(가입은 변경으로 세지 않음). */
    public static Member join(String handle, String nickname, Instant now) {
        return new Member(handle, nickname, now);
    }

    public Long getId() {
        return id;
    }

    public String getHandle() {
        return handle;
    }

    public String getNickname() {
        return nickname;
    }

    public Instant getNicknameChangedAt() {
        return nicknameChangedAt;
    }

    public String getBio() {
        return bio;
    }

    public Role getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public String getDefaultVisibility() {
        return defaultVisibility;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public boolean isLastActiveVisible() {
        return lastActiveVisible;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    /** 기한이 지난 정지를 해제한다(R-23). 정지 상태일 때만 ACTIVE로 돌린다. */
    public void liftSuspension(Instant now) {
        if (status == MemberStatus.SUSPENDED) {
            this.status = MemberStatus.ACTIVE;
            this.updatedAt = now;
        }
    }

    /** 익명 처리(015)되어 더 이상 사람으로 보이지 않는 회원. */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
