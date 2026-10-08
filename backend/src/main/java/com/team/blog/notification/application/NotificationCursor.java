package com.team.blog.notification.application;

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
 * 알림 목록 커서 (011 research R12). 001 {@link CursorCodec}에 정렬 키 {@code [updated_at 마이크로초, id]}를 담는다.
 * 목록 구분은 {@code notifications} — 다른 목록의 커서는 400 {@code INVALID_CURSOR}.
 */
@Component
public class NotificationCursor {

    public static final ListScope SCOPE = ListScope.of("notifications");

    /** 9999-12-31T23:59:59.999999Z의 epoch 마이크로초. */
    private static final long MAX_MICROS = 253_402_300_799_999_999L;

    private final CursorCodec codec;

    public NotificationCursor(CursorCodec codec) {
        this.codec = codec;
    }

    /** 이 알림 다음(더 오래된 쪽)부터. */
    public record Position(Instant updatedAt, long id) {}

    public String next(Instant updatedAt, long id) {
        long micros =
                ChronoUnit.MICROS.between(Instant.EPOCH, updatedAt.truncatedTo(ChronoUnit.MICROS));
        return codec.encode(SCOPE, List.of(micros, id), null);
    }

    /** {@code null}·빈 값이면 {@code null}(처음부터). */
    public Position decode(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        CursorPayload payload = codec.decode(cursor, SCOPE);
        if (payload.k().size() != 2 || !payload.extra().isEmpty()) {
            throw new InvalidCursorException("notification cursor shape");
        }
        long micros = payload.longAt(0);
        long id = payload.longAt(1);
        if (micros < 0 || micros > MAX_MICROS || id < 1) {
            throw new InvalidCursorException("notification cursor out of range");
        }
        try {
            return new Position(Instant.EPOCH.plus(micros, ChronoUnit.MICROS), id);
        } catch (DateTimeException | ArithmeticException e) {
            throw new InvalidCursorException("notification cursor time out of range");
        }
    }
}
