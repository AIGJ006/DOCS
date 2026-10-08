package com.team.blog.post.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.post.domain.ManageCursor;
import com.team.blog.post.domain.ManageTab;
import com.team.blog.shared.web.cursor.CursorCodec;
import com.team.blog.shared.web.cursor.InvalidCursorException;
import com.team.blog.shared.web.cursor.ListScope;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 내 글 관리 커서 (006 T033, research R16). 목록 구분은 001 공용 {@code l}({@link ListScope}) 하나로 한다 — {@code
 * manage:{tab}[:{filter}]}.
 */
class ManageCursorTest {

    private final CursorCodec codec = new CursorCodec();

    private static String json(String raw) {
        return new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
    }

    private static String raw(String json) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest(name = "{0}:{1} → {2}")
    @CsvSource(
            nullValues = "null",
            value = {
                "DRAFTS,all,manage:drafts:all",
                "PUBLISHED,all,manage:published:all",
                "PUBLISHED,public,manage:published:public",
                "PUBLISHED,private,manage:published:private",
                "TRASH,null,manage:trash"
            })
    void 인코딩_디코딩_왕복_마이크로초_보존(ManageTab tab, String filter, String scope) {
        Instant key = Instant.parse("2026-10-03T05:03:12.123456Z");
        ManageCursor cursor = new ManageCursor(tab, filter, key, 42L);

        String raw = cursor.encode(codec);

        assertThat(raw).doesNotContain("=").matches("[A-Za-z0-9_-]+");
        assertThat(json(raw))
                .isEqualTo("{\"v\":1,\"l\":\"" + scope + "\",\"k\":[1791003792123456,42]}");
        assertThat(cursor.scope()).isEqualTo(ListScope.of(scope));
        assertThat(ManageCursor.decode(raw, tab, filter, codec)).isEqualTo(cursor);
    }

    @Test
    void 풀리지_않는_값은_400() {
        for (String bad : List.of("abc", "!!!", "eyJ2IjoxfQ==", "")) {
            assertThatThrownBy(() -> ManageCursor.decode(bad, ManageTab.DRAFTS, "all", codec))
                    .as(bad)
                    .isInstanceOf(InvalidCursorException.class);
        }
    }

    @Test
    void 모르는_버전_키_누락_타입_오류는_400() {
        for (String json :
                List.of(
                        "{\"v\":2,\"l\":\"manage:drafts:all\",\"k\":[1,2]}",
                        "{\"v\":1,\"l\":\"manage:drafts:all\"}",
                        "{\"v\":1,\"l\":\"manage:drafts:all\",\"k\":[1]}",
                        "{\"v\":1,\"l\":\"manage:drafts:all\",\"k\":[1,2,3]}",
                        "{\"v\":1,\"l\":\"manage:drafts:all\",\"k\":[\"x\",2]}",
                        "{\"v\":1,\"l\":\"manage:drafts:all\",\"k\":[1,\"2\"]}",
                        "{\"v\":1,\"l\":\"manage:drafts:all\",\"k\":[-1,2]}",
                        "{\"v\":1,\"l\":\"manage:drafts:all\",\"k\":[1,0]}",
                        "{\"v\":1,\"l\":\"manage:drafts:all\",\"k\":[1.5,2]}")) {
            assertThatThrownBy(() -> ManageCursor.decode(raw(json), ManageTab.DRAFTS, "all", codec))
                    .as(json)
                    .isInstanceOf(InvalidCursorException.class);
        }
    }

    @Test
    void 다른_탭_필터_목록의_커서는_400() {
        Instant key = Instant.parse("2026-10-03T05:03:12.123456Z");
        String publicCursor =
                new ManageCursor(ManageTab.PUBLISHED, "public", key, 7L).encode(codec);
        String trashCursor = new ManageCursor(ManageTab.TRASH, null, key, 7L).encode(codec);
        String homeCursor = codec.encode(ListScope.home(), List.of(1_000_000L, 7L), null);

        assertThatThrownBy(
                        () ->
                                ManageCursor.decode(
                                        publicCursor, ManageTab.PUBLISHED, "private", codec))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(
                        () -> ManageCursor.decode(publicCursor, ManageTab.PUBLISHED, "all", codec))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> ManageCursor.decode(trashCursor, ManageTab.DRAFTS, "all", codec))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> ManageCursor.decode(homeCursor, ManageTab.TRASH, null, codec))
                .isInstanceOf(InvalidCursorException.class);
    }
}
