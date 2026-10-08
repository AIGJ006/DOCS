package com.team.blog.post.domain;

import com.team.blog.shared.web.cursor.CursorCodec;
import com.team.blog.shared.web.cursor.CursorPayload;
import com.team.blog.shared.web.cursor.InvalidCursorException;
import com.team.blog.shared.web.cursor.ListScope;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * 내 글 관리 목록의 이어 보기 위치 (006 T039, research R16). 001 공용 {@link CursorCodec}으로 {@code
 * {"v":1,"l":"manage:{tab}[:{filter}]","k":[epoch 마이크로초, id]}}에 담는다.
 *
 * <p>키 시각은 임시글·발행 글 탭이면 {@code updated_at}, 휴지통이면 {@code deleted_at}이다. 필터는 임시글 {@code all}, 발행 글
 * {@code all|public|private}, 휴지통은 없음({@code null} — {@code manage:trash}). 다른 탭·필터의 커서, 풀리지 않는 값,
 * 키 개수·타입·범위 오류는 모두 {@link InvalidCursorException}(400 {@code INVALID_CURSOR}).
 *
 * @param tab 탭
 * @param filter 필터 (휴지통이면 {@code null}, 그 밖에 {@code null}이면 {@code all})
 * @param key 마지막 줄의 정렬 시각 (마이크로초로 자른다)
 * @param id 마지막 줄의 글 번호
 */
public record ManageCursor(ManageTab tab, String filter, Instant key, long id) {

    /** 필터 없음 (임시글 탭, 발행 글 탭의 [전체]). */
    public static final String ALL = "all";

    /** 9999-12-31T23:59:59.999999Z의 epoch 마이크로초 (005 {@code PostListCursor}와 같은 한계). */
    private static final long MAX_MICROS = 253_402_300_799_999_999L;

    public ManageCursor {
        Objects.requireNonNull(tab, "tab");
        Objects.requireNonNull(key, "key");
        filter = normalize(tab, filter);
        key = key.truncatedTo(ChronoUnit.MICROS);
    }

    /** 이 커서가 속한 목록 구분 값 {@code manage:{tab}[:{filter}]}. */
    public ListScope scope() {
        return scopeOf(tab, filter);
    }

    public String encode(CursorCodec codec) {
        long micros = ChronoUnit.MICROS.between(Instant.EPOCH, key);
        return codec.encode(scope(), List.of(micros, id), null);
    }

    /** 요청한 탭·필터 목록의 커서인지 확인하며 푼다. */
    public static ManageCursor decode(String raw, ManageTab tab, String filter, CursorCodec codec) {
        String normalized = normalize(tab, filter);
        CursorPayload payload = codec.decode(raw, scopeOf(tab, normalized));
        if (payload.k().size() != 2) {
            throw new InvalidCursorException("manage cursor key size " + payload.k().size());
        }
        long micros = payload.longAt(0);
        long id = payload.longAt(1);
        if (micros < 0 || micros > MAX_MICROS) {
            throw new InvalidCursorException("manage cursor time out of range");
        }
        if (id < 1) {
            throw new InvalidCursorException("manage cursor id out of range");
        }
        try {
            return new ManageCursor(
                    tab, normalized, Instant.EPOCH.plus(micros, ChronoUnit.MICROS), id);
        } catch (DateTimeException | ArithmeticException e) {
            throw new InvalidCursorException("manage cursor time out of range");
        }
    }

    private static ListScope scopeOf(ManageTab tab, String filter) {
        return ListScope.of(
                filter == null ? "manage:" + tab.param() : "manage:" + tab.param() + ":" + filter);
    }

    private static String normalize(ManageTab tab, String filter) {
        if (tab == ManageTab.TRASH) {
            return null;
        }
        return filter == null ? ALL : filter;
    }
}
