package com.team.blog.media.application;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 글-사진 연결 임시 구현 (002 T047, data-model §1-4, A-13).
 *
 * <p>// TODO(003): 003 FR-022 확정 후 교체. 작성자({@code uploader_id = authorId})가 올린 글 사진만 연결하고, 남이 올린
 * 사진은 연결하지 않는다(렌더러가 이미 링크로 바꾼다).
 */
@Service
public class ImageService {

    private final JdbcClient jdbc;

    public ImageService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 발행 때 연결을 본문과 맞춘다 (트랜잭션 안). 목록의 작성자 사진은 연결하고 {@code ATTACHED}·{@code detached_at = NULL}로, 이
     * 글에 연결돼 있었으나 목록에서 빠진 사진은 연결을 지우고 다른 글에도 연결이 없으면 {@code detached_at = now()}를 기록한다.
     */
    public void syncPostImages(long postId, long authorId, List<String> ownedKeys) {
        String[] keys = ownedKeys == null ? new String[0] : ownedKeys.toArray(String[]::new);
        List<Long> removed =
                jdbc.sql(
                                """
                                DELETE FROM post_image pi
                                 USING image i
                                 WHERE pi.post_id = :postId AND i.id = pi.image_id
                                   AND NOT (i.storage_key = ANY (CAST(:keys AS varchar[])))
                                RETURNING pi.image_id
                                """)
                        .param("postId", postId)
                        .param("keys", keys)
                        .query(Long.class)
                        .list();
        if (!removed.isEmpty()) {
            jdbc.sql(
                            """
                            UPDATE image SET detached_at = now()
                             WHERE id IN (:ids)
                               AND NOT EXISTS (SELECT 1 FROM post_image pi WHERE pi.image_id = image.id)
                            """)
                    .param("ids", removed)
                    .update();
        }
        attachPostImages(postId, authorId, ownedKeys);
    }

    /** 연결만 더한다 (수동 저장·1분 반영, 003 FR-022). 빠진 사진의 연결은 끊지 않는다 — 발행본이 아직 그 사진을 쓸 수 있다. */
    public void attachPostImages(long postId, long authorId, List<String> ownedKeys) {
        if (ownedKeys == null || ownedKeys.isEmpty()) {
            return;
        }
        String[] keys = ownedKeys.toArray(String[]::new);
        jdbc.sql(
                        """
                        INSERT INTO post_image (post_id, image_id)
                        SELECT :postId, i.id FROM image i
                         WHERE i.storage_key = ANY (CAST(:keys AS varchar[]))
                           AND i.uploader_id = :authorId AND i.purpose = 'POST'
                        ON CONFLICT DO NOTHING
                        """)
                .param("postId", postId)
                .param("keys", keys)
                .param("authorId", authorId)
                .update();
        jdbc.sql(
                        """
                        UPDATE image SET status = 'ATTACHED', detached_at = NULL
                         WHERE storage_key = ANY (CAST(:keys AS varchar[]))
                           AND uploader_id = :authorId AND purpose = 'POST'
                        """)
                .param("keys", keys)
                .param("authorId", authorId)
                .update();
    }
}
