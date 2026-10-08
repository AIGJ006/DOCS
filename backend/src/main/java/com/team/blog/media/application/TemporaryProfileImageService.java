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
 * 프로필 사진 연결 <b>임시 구현</b> (001 T116, data-model §2-6 SQL 그대로). specs/003이 소유·교체한다.
 *
 * <p>크기(정확히 256×256)·1MB·형식 검사는 003 업로드 complete 단계 책임이라 여기서는 하지 않는다. 사진 검사는 "본인이 올림 + {@code
 * purpose = 'PROFILE'} + 행이 있음"뿐이다.
 */
@Service
public class TemporaryProfileImageService implements ProfileImageService {

    private final JdbcClient jdbc;

    public TemporaryProfileImageService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAttachable(long memberId, long imageId) {
        return jdbc.sql(
                                "SELECT count(*) FROM image WHERE id = ? AND uploader_id = ? AND purpose = 'PROFILE'")
                        .params(imageId, memberId)
                        .query(Long.class)
                        .single()
                > 0;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void attach(long memberId, long imageId) {
        detach(memberId);
        int updated =
                jdbc.sql(
                                """
                                UPDATE image SET status = 'ATTACHED', detached_at = NULL
                                 WHERE id = ? AND uploader_id = ? AND purpose = 'PROFILE'
                                """)
                        .params(imageId, memberId)
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
                         WHERE uploader_id = ? AND purpose = 'PROFILE'
                           AND status = 'ATTACHED' AND detached_at IS NULL
                        """)
                .param(memberId)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> currentImageId(long memberId) {
        return jdbc.sql(
                        """
                        SELECT id FROM image
                         WHERE uploader_id = ? AND purpose = 'PROFILE'
                           AND status = 'ATTACHED' AND detached_at IS NULL
                        """)
                .param(memberId)
                .query(Long.class)
                .optional();
    }
}
