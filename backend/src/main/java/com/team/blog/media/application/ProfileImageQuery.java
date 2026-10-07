package com.team.blog.media.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 프로필 사진 조회 (읽기 전용, 003이 소유를 넘겨받는다). 조건은 {@code uq_image_profile_current}와 같다: {@code purpose =
 * 'PROFILE' AND status = 'ATTACHED' AND detached_at IS NULL} — 회원당 최대 1장.
 */
@Service
@Transactional(readOnly = true)
public class ProfileImageQuery {

    private static final String CURRENT =
            "SELECT uploader_id, storage_key, thumb_storage_key FROM image"
                    + " WHERE purpose = 'PROFILE' AND status = 'ATTACHED' AND detached_at IS NULL";

    private final NamedParameterJdbcTemplate jdbc;

    public ProfileImageQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProfileImageKeys> currentKeys(long memberId) {
        return Optional.ofNullable(currentKeysOf(java.util.List.of(memberId)).get(memberId));
    }

    /** 여러 회원의 현재 프로필 사진(SQL 1번). 사진이 없는 회원은 결과에 없다. */
    public Map<Long, ProfileImageKeys> currentKeysOf(Collection<Long> memberIds) {
        Map<Long, ProfileImageKeys> result = new LinkedHashMap<>();
        if (memberIds == null || memberIds.isEmpty()) {
            return result;
        }
        jdbc.query(
                CURRENT + " AND uploader_id IN (:ids)",
                new MapSqlParameterSource("ids", memberIds),
                rs -> {
                    result.put(
                            rs.getLong("uploader_id"),
                            new ProfileImageKeys(
                                    rs.getString("storage_key"),
                                    rs.getString("thumb_storage_key")));
                });
        return result;
    }
}
