package com.team.blog.post.web.dto;

import com.team.blog.post.application.PostTrashService.TrashOutcome;
import java.time.Instant;

/**
 * 글 삭제 응답 (006 T020·T055, contracts {@code TrashedResult}·{@code PurgedResult}).
 *
 * <ul>
 *   <li>{@code DELETE /api/posts/{postId}}: 휴지통으로 옮겼거나 이미 휴지통이면 {@link Trashed}, 빈 임시글을 바로 지웠으면
 *       {@link Purged}.
 *   <li>{@code DELETE /api/posts/{postId}/permanent}(영구 삭제): 항상 {@link Purged}.
 * </ul>
 */
public sealed interface TrashResponse {

    /** {@code {trashed: true, purgeAt}} — {@code purgeAt = deletedAt + 보관 기간}. */
    record Trashed(boolean trashed, Instant purgeAt) implements TrashResponse {
        public Trashed(Instant purgeAt) {
            this(true, purgeAt);
        }
    }

    /** {@code {purged: true}}. */
    record Purged(boolean purged) implements TrashResponse {
        public static final Purged INSTANCE = new Purged(true);
    }

    static TrashResponse from(TrashOutcome outcome) {
        return switch (outcome) {
            case TrashOutcome.Trashed trashed -> new Trashed(trashed.purgeAt());
            case TrashOutcome.Purged purged -> Purged.INSTANCE;
        };
    }
}
