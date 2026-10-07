package com.team.blog.discovery.application;

import com.team.blog.shared.web.cursor.CursorCodec;
import com.team.blog.shared.web.cursor.CursorPayload;
import com.team.blog.shared.web.cursor.InvalidCursorException;
import com.team.blog.shared.web.cursor.ListScope;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 홈·블로그 목록 커서 (005 T007, research R-05·R-24, FR-006). 정렬 키 {@code (first_public_at, id)}를 001 공용
 * {@link CursorCodec}으로 {@code {"v":1,"l":"home"|"blog:{handle}","k":[epoch 마이크로초(UTC), id]}}에 담는다.
 *
 * <p>시각은 PostgreSQL {@code timestamptz} 정밀도와 같은 마이크로초로 다룬다 — 밀리초로 자르면 같은 밀리초 글이 빠진다. 다른 목록의 커서·키
 * 개수·타입·범위가 틀리면 {@link InvalidCursorException}(400 {@code INVALID_CURSOR}).
 */
@Component
public class PostListCursor {

    /** 9999-12-31T23:59:59.999999Z의 epoch 마이크로초. 이보다 크거나 음수인 시각은 커서로 받지 않는다. */
    static final long MAX_MICROS = 253_402_300_799_999_999L;

    private final CursorCodec codec;

    public PostListCursor(CursorCodec codec) {
        this.codec = codec;
    }

    /**
     * 이어 보기 위치 (마지막 카드의 정렬 값).
     *
     * @param firstPublicAt 최초 공개 일자 (UTC, 마이크로초)
     * @param id 글 번호
     */
    public record CursorKey(OffsetDateTime firstPublicAt, long id) {}

    public String encode(ListScope scope, OffsetDateTime firstPublicAt, long id) {
        return codec.encode(scope, List.of(toMicros(firstPublicAt.toInstant()), id), null);
    }

    public String encode(ListScope scope, Instant firstPublicAt, long id) {
        return codec.encode(scope, List.of(toMicros(firstPublicAt), id), null);
    }

    /**
     * @param cursor 클라이언트가 돌려준 값 ({@code null}·빈 값이면 {@code null} — 첫 페이지)
     * @param expected 요청한 목록
     */
    public CursorKey decode(String cursor, ListScope expected) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        CursorPayload payload = codec.decode(cursor, expected);
        if (payload.k().size() != 2) {
            throw new InvalidCursorException("post list cursor key size " + payload.k().size());
        }
        long micros = payload.longAt(0);
        long id = payload.longAt(1);
        if (micros < 0 || micros > MAX_MICROS) {
            throw new InvalidCursorException("post list cursor time out of range");
        }
        if (id < 1) {
            throw new InvalidCursorException("post list cursor id out of range");
        }
        try {
            return new CursorKey(
                    Instant.EPOCH.plus(micros, ChronoUnit.MICROS).atOffset(ZoneOffset.UTC), id);
        } catch (DateTimeException | ArithmeticException e) {
            throw new InvalidCursorException("post list cursor time out of range");
        }
    }

    private static long toMicros(Instant instant) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, instant.truncatedTo(ChronoUnit.MICROS));
    }
}
