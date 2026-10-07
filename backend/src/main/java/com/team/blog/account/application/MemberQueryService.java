package com.team.blog.account.application;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 모듈이 쓰는 회원 읽기 API (모듈 경계 — 다른 모듈은 account의 Repository를 직접 부르지 않는다).
 *
 * <ul>
 *   <li>{@link #findAccessInfo(long)} — 004 {@code Viewer}·T042a 탈퇴 유예 필터
 *   <li>{@link #findReadableBlogOwner(String)}·{@link #normalizeHandle(String)} — 005 블로그 주소
 *   <li>{@link #defaultVisibility(long)} — 002 새 글 기본 공개 범위
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class MemberQueryService {

    private final JdbcClient jdbc;

    public MemberQueryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 역할·상태·이메일 인증 여부 (PK 1번 조회). 없는 회원과 익명 처리된({@code deleted_at}) 회원은 빈 값 — 비로그인으로 본다. */
    public Optional<MemberAccessInfo> findAccessInfo(long memberId) {
        return jdbc.sql(
                        """
                        SELECT m.role, m.status, a.email_verified_at IS NOT NULL AS verified
                          FROM member m
                          LEFT JOIN auth_identity a ON a.member_id = m.id
                         WHERE m.id = ? AND m.deleted_at IS NULL
                        """)
                .param(memberId)
                .query(
                        (rs, n) ->
                                new MemberAccessInfo(
                                        Role.valueOf(rs.getString("role")),
                                        MemberStatus.valueOf(rs.getString("status")),
                                        rs.getBoolean("verified")))
                .optional();
    }

    /**
     * 주소로 읽을 수 있는 블로그 주인을 찾는다. 탈퇴 유예({@code WITHDRAWN})·익명 처리·없는 주소는 빈 값(005 404, FR-021).
     *
     * <p>저장된 주소와 정확히 같은 값만 찾는다. 대문자가 섞인 주소는 005가 먼저 {@link #normalizeHandle(String)}로 소문자 주소에 301을
     * 보내므로 여기서는 없음으로 처리한다(005 T048).
     */
    public Optional<BlogOwner> findReadableBlogOwner(String handle) {
        if (handle == null || handle.isBlank() || handle.length() > 39) {
            return Optional.empty();
        }
        return jdbc.sql(
                        """
                        SELECT id, handle, nickname, bio
                          FROM member
                         WHERE handle = ? AND status <> 'WITHDRAWN' AND deleted_at IS NULL
                        """)
                .param(handle)
                .query(
                        (rs, n) ->
                                new BlogOwner(
                                        rs.getLong("id"),
                                        rs.getString("handle"),
                                        rs.getString("nickname"),
                                        rs.getString("bio")))
                .optional();
    }

    /** 새 글 기본 공개 범위({@code PUBLIC}/{@code PRIVATE}). 없는 회원이면 {@code IllegalArgumentException}. */
    public String defaultVisibility(long memberId) {
        return jdbc.sql("SELECT default_visibility FROM member WHERE id = ?")
                .param(memberId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("없는 회원입니다: " + memberId));
    }

    /** 주소 비교·301 판단용 소문자화 (FR-021). 주소는 영문 소문자·숫자·{@code _}·{@code -}만 쓰므로 {@link Locale#ROOT}. */
    public static String normalizeHandle(String handle) {
        return handle == null ? null : handle.toLowerCase(Locale.ROOT);
    }
}
