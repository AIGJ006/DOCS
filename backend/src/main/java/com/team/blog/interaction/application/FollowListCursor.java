package com.team.blog.interaction.application;

import com.team.blog.shared.web.cursor.CursorCodec;
import com.team.blog.shared.web.cursor.CursorPayload;
import com.team.blog.shared.web.cursor.InvalidCursorException;
import com.team.blog.shared.web.cursor.ListScope;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 팔로워·팔로잉 목록 커서 (010 T008, data-model §4, research R6). 001 {@link CursorCodec}에 정렬 키 {@code
 * [follow.created_at epoch 마이크로초, 회원 번호]}를 담는다. 목록 구분은 {@link ListScope#followers(String)}·{@link
 * ListScope#following(String)} — 다른 목록·다른 주소의 커서, 키 개수·범위가 틀린 커서는 400 {@code INVALID_CURSOR}.
 *
 * <p>시각은 PostgreSQL {@code timestamptz} 정밀도와 같은 마이크로초로 다룬다(005 {@code PostListCursor}와 같은 이유).
 */
@Component
public class FollowListCursor {

    /** 9999-12-31T23:59:59.999999Z의 epoch 마이크로초. */
    static final long MAX_MICROS = 253_402_300_799_999_999L;

    private final CursorCodec codec;

    public FollowListCursor(CursorCodec codec) {
        this.codec = codec;
    }

    /**
     * 이어 보기 위치 (마지막 항목의 정렬 값).
     *
     * @param createdAt 팔로우한 시각 (마이크로초)
     * @param memberId 그 항목의 회원 번호 (팔로워 목록은 팔로우한 사람, 팔로잉 목록은 팔로우받은 사람)
     */
    public record Position(Instant createdAt, long memberId) {}

    public String encode(ListScope scope, Instant createdAt, long memberId) {
        return codec.encode(scope, List.of(toMicros(createdAt), memberId), null);
    }

    /**
     * @param cursor 클라이언트가 돌려준 값 ({@code null}·빈 값이면 {@code null} — 첫 페이지)
     * @param expected 요청한 목록
     */
    public Position decode(String cursor, ListScope expected) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        CursorPayload payload = codec.decode(cursor, expected);
        if (payload.k().size() != 2) {
            throw new InvalidCursorException("follow cursor key size " + payload.k().size());
        }
        long micros = payload.longAt(0);
        long memberId = payload.longAt(1);
        if (micros < 0 || micros > MAX_MICROS || memberId < 1) {
            throw new InvalidCursorException("follow cursor out of range");
        }
        try {
            return new Position(Instant.EPOCH.plus(micros, ChronoUnit.MICROS), memberId);
        } catch (DateTimeException | ArithmeticException e) {
            throw new InvalidCursorException("follow cursor time out of range");
        }
    }

    private static long toMicros(Instant instant) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, instant.truncatedTo(ChronoUnit.MICROS));
    }
}
