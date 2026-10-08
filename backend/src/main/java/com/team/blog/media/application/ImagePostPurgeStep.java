package com.team.blog.media.application;

import com.team.blog.post.application.spi.PostPurgeStep;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 완전 삭제 전 사진 연결 해제 (006 T060에서 시작, <b>003 소유</b> — 003 T046, contracts/storage.md §3-1, 006
 * research R12, 13 §2-5 FR-032). 회귀 테스트: {@code ImagePostPurgeStepIT}.
 *
 * <p>지울 글에만 연결된 사진의 {@code image.detached_at}을 채운다. 다른 글(정상·휴지통 무관)과 함께 쓰는 사진은 그대로 둔다. {@code
 * post_image} 행은 이어지는 {@code DELETE FROM post}의 CASCADE가 지운다. 파일은 여기서 지우지 않는다 — 트랜잭션 안에서 외부 호출을 하지
 * 않으며, 003 정리 배치가 {@code detached_at} 7일 뒤에 지운다.
 */
@Component
public class ImagePostPurgeStep implements PostPurgeStep {

    static final int ORDER = 20;

    private final NamedParameterJdbcTemplate jdbc;

    public ImagePostPurgeStep(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void beforePurge(long postId) {
        jdbc.update(
                "UPDATE image i SET detached_at = now()"
                        + " WHERE i.detached_at IS NULL"
                        + " AND EXISTS (SELECT 1 FROM post_image pi"
                        + "             WHERE pi.image_id = i.id AND pi.post_id = :id)"
                        + " AND NOT EXISTS (SELECT 1 FROM post_image pi"
                        + "                 WHERE pi.image_id = i.id AND pi.post_id <> :id)",
                Map.of("id", postId));
    }
}
