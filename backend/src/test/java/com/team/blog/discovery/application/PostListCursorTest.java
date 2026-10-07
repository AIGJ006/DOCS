package com.team.blog.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.discovery.application.PostListCursor.CursorKey;
import com.team.blog.shared.web.cursor.CursorCodec;
import com.team.blog.shared.web.cursor.InvalidCursorException;
import com.team.blog.shared.web.cursor.ListScope;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * 목록 커서 변환 (005 T006, research R-05·R-24, FR-006). 손상 문자열·모르는 {@code v}·{@code l} 불일치는 001 {@code
 * CursorCodecTest}가 이미 확인한다.
 */
class PostListCursorTest {

    private final PostListCursor cursors = new PostListCursor(new CursorCodec());

    private static String raw(String json) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String json(String cursor) {
        return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    }

    @Test
    void 홈_커서는_마이크로초_정수와_글_번호를_담는다() {
        OffsetDateTime at = OffsetDateTime.parse("2026-09-30T08:00:00.123456Z");

        String cursor = cursors.encode(ListScope.home(), at, 37);

        assertThat(cursor).doesNotContain("=").matches("^[A-Za-z0-9_-]+$");
        assertThat(json(cursor)).isEqualTo("{\"v\":1,\"l\":\"home\",\"k\":[1790755200123456,37]}");
    }

    @Test
    void 마이크로초를_잃지_않고_왕복한다() {
        OffsetDateTime at = OffsetDateTime.parse("2026-10-02T14:03:12.123456Z");

        CursorKey key = cursors.decode(cursors.encode(ListScope.home(), at, 42), ListScope.home());

        assertThat(key.firstPublicAt()).isEqualTo(at);
        assertThat(key.firstPublicAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(key.firstPublicAt().toString()).isEqualTo("2026-10-02T14:03:12.123456Z");
        assertThat(key.id()).isEqualTo(42);
    }

    @Test
    void 다른_시간대의_시각도_UTC_마이크로초로_바꾼다() {
        OffsetDateTime kst = OffsetDateTime.parse("2026-10-02T23:03:12.000001+09:00");

        CursorKey key =
                cursors.decode(
                        cursors.encode(ListScope.blog("kim755030"), kst, 5),
                        ListScope.blog("kim755030"));

        assertThat(key.firstPublicAt())
                .isEqualTo(OffsetDateTime.parse("2026-10-02T14:03:12.000001Z"));
    }

    @Test
    void 나노초는_마이크로초로_자른다() {
        OffsetDateTime at = OffsetDateTime.parse("2026-10-02T14:03:12.123456789Z");

        CursorKey key = cursors.decode(cursors.encode(ListScope.home(), at, 1), ListScope.home());

        assertThat(key.firstPublicAt())
                .isEqualTo(OffsetDateTime.parse("2026-10-02T14:03:12.123456Z"));
    }

    @Test
    void 블로그_범위_커서를_홈에서_풀면_거부한다() {
        String blog =
                cursors.encode(
                        ListScope.blog("kim755030"),
                        OffsetDateTime.parse("2026-10-02T00:00:00Z"),
                        3);

        assertThatThrownBy(() -> cursors.decode(blog, ListScope.home()))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void 키_길이가_2가_아니면_거부한다() {
        assertThatThrownBy(
                        () ->
                                cursors.decode(
                                        raw("{\"v\":1,\"l\":\"home\",\"k\":[1790755200123456]}"),
                                        ListScope.home()))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(
                        () ->
                                cursors.decode(
                                        raw("{\"v\":1,\"l\":\"home\",\"k\":[1,2,3]}"),
                                        ListScope.home()))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void 시각이_정수가_아니면_거부한다() {
        assertThatThrownBy(
                        () ->
                                cursors.decode(
                                        raw("{\"v\":1,\"l\":\"home\",\"k\":[\"2026-10-02\",3]}"),
                                        ListScope.home()))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(
                        () ->
                                cursors.decode(
                                        raw("{\"v\":1,\"l\":\"home\",\"k\":[1.5,3]}"),
                                        ListScope.home()))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void 글_번호가_1보다_작거나_정수가_아니면_거부한다() {
        for (String id : new String[] {"0", "-1", "\"3\"", "null"}) {
            String cursor = raw("{\"v\":1,\"l\":\"home\",\"k\":[1790755200123456," + id + "]}");
            assertThatThrownBy(() -> cursors.decode(cursor, ListScope.home()))
                    .as(id)
                    .isInstanceOf(InvalidCursorException.class);
        }
    }

    @Test
    void 표현할_수_없는_시각은_거부한다() {
        String cursor = raw("{\"v\":1,\"l\":\"home\",\"k\":[9223372036854775807,3]}");

        assertThatThrownBy(() -> cursors.decode(cursor, ListScope.home()))
                .isInstanceOf(InvalidCursorException.class);
    }
}
