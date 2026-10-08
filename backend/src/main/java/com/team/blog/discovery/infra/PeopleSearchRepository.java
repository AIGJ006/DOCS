package com.team.blog.discovery.infra;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 사람 검색 SQL (012 T034, research R10, contracts §7). 닉네임·블로그 주소의 대소문자 무시 부분 일치({@code ILIKE}, GIN
 * {@code ix_member_nickname_trgm}·{@code ix_member_handle_trgm}). 탈퇴 신청({@code withdrawn_at})·익명
 * 처리({@code deleted_at}) 회원은 빼고 정지 회원은 넣는다(블로그가 보이므로). 정확히 일치(닉네임 대소문자 무시·주소) 먼저, 그다음 닉네임 가나다 → 회원
 * 번호(제안 — T003).
 *
 * <p><b>원칙 II 읽기 예외 (plan Complexity Tracking 2행)</b>: 결과에 닉네임·주소·소개·사진을 한 번에 보여 주려고 {@code
 * member}·{@code image}를 읽기 전용으로 읽는다. 사진 JOIN 조건은 005 카드 SQL과 같다({@code uq_image_profile_current}).
 */
@Repository
public class PeopleSearchRepository {

    private final JdbcClient jdbc;

    public PeopleSearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 결과 한 줄 (사진은 저장소 키). */
    public record PersonRow(String handle, String nickname, String bio, String profileKey) {}

    public List<PersonRow> search(String token, int limit) {
        return jdbc.sql(
                        """
                        SELECT m.handle, m.nickname, m.bio,
                               COALESCE(pi.thumb_storage_key, pi.storage_key) AS profile_key
                          FROM member m
                          LEFT JOIN image pi ON pi.uploader_id = m.id AND pi.purpose = 'PROFILE'
                               AND pi.status = 'ATTACHED' AND pi.detached_at IS NULL
                         WHERE m.withdrawn_at IS NULL AND m.deleted_at IS NULL
                           AND (m.nickname ILIKE :pat ESCAPE '\\' OR m.handle ILIKE :pat ESCAPE '\\')
                         ORDER BY (lower(m.nickname) = lower(:token) OR m.handle = lower(:token)) DESC,
                                  lower(m.nickname), m.id
                         LIMIT :limit
                        """)
                .param("pat", PostSearchRepository.pattern(token))
                .param("token", token)
                .param("limit", limit)
                .query(
                        (rs, n) ->
                                new PersonRow(
                                        rs.getString("handle"),
                                        rs.getString("nickname"),
                                        rs.getString("bio"),
                                        rs.getString("profile_key")))
                .list();
    }
}
