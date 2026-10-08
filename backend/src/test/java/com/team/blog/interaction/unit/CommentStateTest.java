package com.team.blog.interaction.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.domain.CommentState;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 댓글 표시 상태 (007 T004, data-model §3, FR-019·FR-020). */
class CommentStateTest {

    private static final Instant T = Instant.parse("2026-10-08T00:00:00Z");

    @Test
    void 우선순위는_탈퇴_삭제_숨김_정상() {
        assertThat(CommentState.of(true, T, T)).isEqualTo(CommentState.WITHDRAWN_AUTHOR);
        assertThat(CommentState.of(false, T, T)).isEqualTo(CommentState.DELETED);
        assertThat(CommentState.of(false, null, T)).isEqualTo(CommentState.HIDDEN);
        assertThat(CommentState.of(false, null, null)).isEqualTo(CommentState.NORMAL);
    }

    @Test
    void 탈퇴_작성자의_숨김_댓글은_탈퇴로() {
        assertThat(CommentState.of(true, null, T)).isEqualTo(CommentState.WITHDRAWN_AUTHOR);
    }

    @Test
    void 숨김은_작성자_본인에게만_내용을_보인다() {
        assertThat(CommentState.HIDDEN.reveals(true)).isTrue();
        assertThat(CommentState.HIDDEN.reveals(false)).isFalse();
        assertThat(CommentState.NORMAL.reveals(false)).isTrue();
        assertThat(CommentState.DELETED.reveals(true)).isFalse();
        assertThat(CommentState.WITHDRAWN_AUTHOR.reveals(true)).isFalse();
    }

    @Test
    void 정상_댓글에만_답글을_단다() {
        assertThat(CommentState.NORMAL.acceptsReply()).isTrue();
        assertThat(CommentState.HIDDEN.acceptsReply()).isFalse();
        assertThat(CommentState.DELETED.acceptsReply()).isFalse();
        assertThat(CommentState.WITHDRAWN_AUTHOR.acceptsReply()).isFalse();
    }

    @Test
    void 수정됨은_updated_at이_created_at보다_뒤일_때() {
        assertThat(CommentState.edited(T, T)).isFalse();
        assertThat(CommentState.edited(T, T.plusNanos(1000))).isTrue();
    }
}
