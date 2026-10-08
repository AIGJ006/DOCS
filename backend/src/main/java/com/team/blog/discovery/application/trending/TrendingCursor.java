package com.team.blog.discovery.application.trending;

import com.team.blog.shared.web.cursor.CursorCodec;
import com.team.blog.shared.web.cursor.CursorPayload;
import com.team.blog.shared.web.cursor.InvalidCursorException;
import com.team.blog.shared.web.cursor.ListScope;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 트렌딩 커서 (012 T027, data-model §4, research R5). 001 {@link CursorCodec}에 목록 구분 {@code trending}, 키
 * {@code [스냅샷 ID, 위치]}를 담는다(원문 {@code {스냅샷ID}:{위치}}를 O8 불투명 커서로 감쌈). 다른 목록의 커서·형식이 틀린 값은 400 {@code
 * INVALID_CURSOR}.
 */
@Component
public class TrendingCursor {

    public static final ListScope SCOPE = ListScope.of("trending");

    private static final Pattern SNAPSHOT_ID = Pattern.compile("^\\d{12}$");
    private static final long MAX_POSITION = 10_000;

    private final CursorCodec codec;

    public TrendingCursor(CursorCodec codec) {
        this.codec = codec;
    }

    /**
     * @param snapshotId 스냅샷 ID ({@code yyyyMMddHHmm})
     * @param position 다음에 읽을 위치 (0부터)
     */
    public record Key(String snapshotId, long position) {}

    public String encode(Key key) {
        return codec.encode(SCOPE, List.of(key.snapshotId(), key.position()), null);
    }

    /** 커서가 없거나 비면 {@code null}. */
    public Key decode(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        CursorPayload payload = codec.decode(cursor, SCOPE);
        if (payload.k().size() != 2) {
            throw new InvalidCursorException("trending cursor key size " + payload.k().size());
        }
        String snapshotId = payload.stringAt(0);
        long position = payload.longAt(1);
        if (!SNAPSHOT_ID.matcher(snapshotId).matches() || position < 0 || position > MAX_POSITION) {
            throw new InvalidCursorException("trending cursor out of range");
        }
        return new Key(snapshotId, position);
    }
}
