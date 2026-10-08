package com.team.blog.discovery.application.search;

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
 * 글 검색 커서 (012 data-model §4, research R8). 001 {@link CursorCodec}에 목록 구분 {@code
 * search:{relevance|latest}:{blogOwnerId|-}:{지문}}, 키 {@code [단계, first_public_at 마이크로초(UTC), id]}를
 * 담는다. 다른 검색어·정렬·블로그의 커서, 정렬에 없는 단계는 400 {@code INVALID_CURSOR}.
 */
@Component
public class SearchCursor {

    /** 9999-12-31T23:59:59.999999Z의 epoch 마이크로초. */
    static final long MAX_MICROS = 253_402_300_799_999_999L;

    private final CursorCodec codec;

    public SearchCursor(CursorCodec codec) {
        this.codec = codec;
    }

    /**
     * 이어 보기 위치.
     *
     * @param stage 마지막 결과의 단계
     * @param firstPublicAt 마지막 결과의 최초 공개 일자 (UTC, 마이크로초)
     * @param id 마지막 결과의 글 번호
     */
    public record Key(SearchStage stage, OffsetDateTime firstPublicAt, long id) {}

    public static ListScope scope(SearchSort sort, Long blogOwnerId, String fingerprint) {
        return ListScope.of(
                "search:"
                        + sort.value()
                        + ":"
                        + (blogOwnerId == null ? "-" : blogOwnerId.toString())
                        + ":"
                        + fingerprint);
    }

    public String encode(ListScope scope, Key key) {
        Instant at = key.firstPublicAt().toInstant().truncatedTo(ChronoUnit.MICROS);
        long micros = ChronoUnit.MICROS.between(Instant.EPOCH, at);
        return codec.encode(scope, List.of(key.stage().code(), micros, key.id()), null);
    }

    /** 첫 페이지(커서 없음·빈 값)면 {@code null}. */
    public Key decode(String cursor, ListScope expected, SearchSort sort) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        CursorPayload payload = codec.decode(cursor, expected);
        if (payload.k().size() != 3) {
            throw new InvalidCursorException("search cursor key size " + payload.k().size());
        }
        SearchStage stage = SearchStage.ofCode(payload.longAt(0));
        boolean stageFits =
                stage != null && (sort == SearchSort.LATEST) == (stage == SearchStage.ANY);
        if (!stageFits) {
            throw new InvalidCursorException("search cursor stage does not fit sort");
        }
        long micros = payload.longAt(1);
        long id = payload.longAt(2);
        if (micros < 0 || micros > MAX_MICROS || id < 1) {
            throw new InvalidCursorException("search cursor key out of range");
        }
        try {
            return new Key(
                    stage,
                    Instant.EPOCH.plus(micros, ChronoUnit.MICROS).atOffset(ZoneOffset.UTC),
                    id);
        } catch (DateTimeException | ArithmeticException e) {
            throw new InvalidCursorException("search cursor time out of range");
        }
    }
}
