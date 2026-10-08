package com.team.blog.interaction.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.interaction.application.CommentCursor;
import com.team.blog.shared.web.cursor.CursorCodec;
import com.team.blog.shared.web.cursor.InvalidCursorException;
import com.team.blog.shared.web.cursor.ListScope;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 댓글 커서 (007 T005, research R11). */
class CommentCursorTest {

    private final CursorCodec codec = new CursorCodec();
    private final CommentCursor cursor = new CommentCursor(codec);
    private final Instant at = Instant.parse("2026-10-08T01:02:03.123456Z");

    @Test
    void 마이크로초까지_왕복한다() {
        String value = cursor.next(CommentCursor.commentsOf(7), at, 42);
        CommentCursor.Position position = cursor.decode(value, CommentCursor.commentsOf(7));
        assertThat(position.createdAt()).isEqualTo(at);
        assertThat(position.id()).isEqualTo(42);
        assertThat(position.previous()).isFalse();
    }

    @Test
    void 이전_방향_커서() {
        String value = cursor.previous(CommentCursor.commentsOf(7), at, 42);
        assertThat(cursor.decode(value, CommentCursor.commentsOf(7)).previous()).isTrue();
    }

    @Test
    void 다른_글_다른_목록_home_커서는_400() {
        String comments = cursor.next(CommentCursor.commentsOf(7), at, 42);
        assertThatThrownBy(() -> cursor.decode(comments, CommentCursor.commentsOf(8)))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> cursor.decode(comments, CommentCursor.repliesOf(7)))
                .isInstanceOf(InvalidCursorException.class);
        String home = codec.encode(ListScope.home(), List.of(1L, 2L), null);
        assertThatThrownBy(() -> cursor.decode(home, CommentCursor.commentsOf(7)))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void 답글_커서는_replies_구분() {
        String value = cursor.next(CommentCursor.repliesOf(5), at, 9);
        assertThat(cursor.decode(value, CommentCursor.repliesOf(5)).id()).isEqualTo(9);
        assertThatThrownBy(() -> cursor.decode(value, CommentCursor.commentsOf(5)))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void 비거나_null이면_처음부터() {
        assertThat(cursor.decode(null, CommentCursor.commentsOf(1))).isNull();
        assertThat(cursor.decode("", CommentCursor.commentsOf(1))).isNull();
    }

    @Test
    void 키_모양이_틀리면_400() {
        String bad = codec.encode(CommentCursor.commentsOf(1), List.of(1L), null);
        assertThatThrownBy(() -> cursor.decode(bad, CommentCursor.commentsOf(1)))
                .isInstanceOf(InvalidCursorException.class);
        String negative = codec.encode(CommentCursor.commentsOf(1), List.of(-1L, 1L), null);
        assertThatThrownBy(() -> cursor.decode(negative, CommentCursor.commentsOf(1)))
                .isInstanceOf(InvalidCursorException.class);
    }
}
