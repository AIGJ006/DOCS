package com.team.blog.media.application;

import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.ValidationException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 사진 연결 (003 T041 — 001 T116 임시 구현 {@code TemporaryProfileImageService}를 대신함, research R14,
 * contracts/storage.md §3-3, 001 data-model §2-6).
 *
 * <p>붙일 수 있는 사진 = 그 회원이 올린 {@code purpose = 'PROFILE'}이면서 완료 확인(정확히 256×256·1MB·WebP/JPEG)을 통과한
 * 것({@code width IS NOT NULL}). 크기·형식은 {@link ImageUploadService#complete}가 이미 확인했다.
 *
 * <p>{@link #attach}·{@link #detach}는 001 프로필 저장 트랜잭션 안에서, 회원 행을 {@code FOR UPDATE}로 잠근 뒤({@code
 * MemberLockService}·{@code ProfileService}) 부른다 — 같은 회원의 저장이 직렬화되어 {@code
 * uq_image_profile_current}가 깨지지 않는다. 순서는 "떼기 → 붙이기"다(부분 유일 인덱스). 뗀 사진은 정리 작업이 7일 뒤 지운다.
 */
@Service
public class DefaultProfileImageService implements ProfileImageService {

    private final JdbcClient jdbc;

    public DefaultProfileImageService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAttachable(long memberId, long imageId) {
        return jdbc.sql(
                                """
                                SELECT count(*) FROM image
                                 WHERE id = :id AND uploader_id = :me AND purpose = 'PROFILE'
                                   AND width IS NOT NULL
                                """)
                        .param("id", imageId)
                        .param("me", memberId)
                        .query(Long.class)
                        .single()
                > 0;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void attach(long memberId, long imageId) {
        Optional<Long> current = currentImageId(memberId);
        if (current.isPresent() && current.get() == imageId) {
            return;
        }
        detach(memberId);
        int updated =
                jdbc.sql(
                                """
                                UPDATE image SET status = 'ATTACHED', detached_at = NULL
                                 WHERE id = :id AND uploader_id = :me AND purpose = 'PROFILE'
                                   AND width IS NOT NULL
                                """)
                        .param("id", imageId)
                        .param("me", memberId)
                        .update();
        if (updated == 0) {
            throw new ValidationException(
                    List.of(
                            new FieldError(
                                    "profileImageId",
                                    INVALID_PROFILE_IMAGE,
                                    INVALID_PROFILE_IMAGE_MESSAGE)));
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void detach(long memberId) {
        jdbc.sql(
                        """
                        UPDATE image SET detached_at = now()
                         WHERE uploader_id = :me AND purpose = 'PROFILE'
                           AND status = 'ATTACHED' AND detached_at IS NULL
                        """)
                .param("me", memberId)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> currentImageId(long memberId) {
        return jdbc.sql(
                        """
                        SELECT id FROM image
                         WHERE uploader_id = :me AND purpose = 'PROFILE'
                           AND status = 'ATTACHED' AND detached_at IS NULL
                        """)
                .param("me", memberId)
                .query(Long.class)
                .optional();
    }
}
