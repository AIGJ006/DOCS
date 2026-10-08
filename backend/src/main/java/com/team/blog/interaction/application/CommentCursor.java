package com.team.blog.interaction.application;

import com.team.blog.shared.web.cursor.CursorCodec;
import com.team.blog.shared.web.cursor.CursorPayload;
import com.team.blog.shared.web.cursor.InvalidCursorException;
import com.team.blog.shared.web.cursor.ListScope;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 댓글·답글 커서 (007 research R11). 001 {@link CursorCodec}에 정렬 키 {@code [epoch 마이크로초, id]}를 담는다. 목록 구분은
 * 최상위 {@code comments:{postId}}, 답글 {@code replies:{rootId}} — 다른 글·다른 목록의 커서는 400 {@code
 * INVALID_CURSOR}. 이전 방향(바로 가기의 [이전 댓글 보기])은 추가 필드 {@code "d":"prev"}로 구분한다.
 */
@Component
public class CommentCursor {

    /** 9999-12-31T23:59:59.999999Z의 epoch 마이크로초. */
    static final long MAX_MICROS = 253_402_300_799_999_999L;

    private static final String DIRECTION = "d";
    private static final String PREV = "prev";

    private final CursorCodec codec;

    public CommentCursor(CursorCodec codec) {
        this.codec = codec;
    }

    /**
     * 풀린 위치.
     *
     * @param createdAt 기준 댓글의 작성 시각 (마이크로초)
     * @param id 기준 댓글 번호
     * @param previous 이전 방향이면 {@code true} — 기준보다 앞의 댓글을 준다
     */
    public record Position(Instant createdAt, long id, boolean previous) {}

    public static ListScope commentsOf(long postId) {
        return ListScope.of("comments:" + postId);
    }

    public static ListScope repliesOf(long rootId) {
        return ListScope.of("replies:" + rootId);
    }

    /** 이 댓글 다음부터. */
    public String next(ListScope scope, Instant createdAt, long id) {
        return codec.encode(scope, List.of(toMicros(createdAt), id), null);
    }

    /** 이 댓글 앞쪽. */
    public String previous(ListScope scope, Instant createdAt, long id) {
        return codec.encode(scope, List.of(toMicros(createdAt), id), Map.of(DIRECTION, PREV));
    }

    /** {@code null}·빈 값이면 {@code null}(처음부터). */
    public Position decode(String cursor, ListScope expected) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        CursorPayload payload = codec.decode(cursor, expected);
        if (payload.k().size() != 2) {
            throw new InvalidCursorException("comment cursor key size " + payload.k().size());
        }
        long micros = payload.longAt(0);
        long id = payload.longAt(1);
        if (micros < 0 || micros > MAX_MICROS || id < 1) {
            throw new InvalidCursorException("comment cursor out of range");
        }
        Object direction = payload.extra().get(DIRECTION);
        if (direction != null && !PREV.equals(direction)) {
            throw new InvalidCursorException("comment cursor direction");
        }
        try {
            return new Position(
                    Instant.EPOCH.plus(micros, ChronoUnit.MICROS), id, direction != null);
        } catch (DateTimeException | ArithmeticException e) {
            throw new InvalidCursorException("comment cursor time out of range");
        }
    }

    static long toMicros(Instant instant) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, instant.truncatedTo(ChronoUnit.MICROS));
    }
}
