package com.team.blog.shared.web.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.shared.error.CommonReasonCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 불투명 커서: Base64URL(패딩 없음) JSON {"v":1,"l":목록 구분,"k":[키…]} (005 R-24, 006 R16). */
class CursorCodecTest {

    private final CursorCodec codec = new CursorCodec();

    private static String b64(String json) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 홈_커서는_정해진_JSON을_패딩_없는_Base64URL로_인코딩한다() {
        String cursor = codec.encode(ListScope.home(), List.of(1790755200123456L, 37L), Map.of());
        assertThat(cursor).doesNotContain("=", "+", "/");
        assertThat(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8))
                .isEqualTo("{\"v\":1,\"l\":\"home\",\"k\":[1790755200123456,37]}");
        assertThat(cursor).isEqualTo(b64("{\"v\":1,\"l\":\"home\",\"k\":[1790755200123456,37]}"));
    }

    @Test
    void 인코딩_디코딩_왕복() {
        String cursor = codec.encode(ListScope.home(), List.of(1790755200123456L, 37L), Map.of());
        CursorPayload payload = codec.decode(cursor, ListScope.home());
        assertThat(payload.v()).isEqualTo(1);
        assertThat(payload.l()).isEqualTo("home");
        assertThat(payload.k()).containsExactly(1790755200123456L, 37L);
        assertThat(payload.extra()).isEmpty();
        assertThat(payload.longAt(0)).isEqualTo(1790755200123456L);
        assertThat(payload.longAt(1)).isEqualTo(37L);
    }

    @Test
    void 작은_정수도_Long으로_돌려준다() {
        CursorPayload payload =
                codec.decode(b64("{\"v\":1,\"l\":\"home\",\"k\":[5,\"x\"]}"), ListScope.home());
        assertThat(payload.k().get(0)).isInstanceOf(Long.class).isEqualTo(5L);
        assertThat(payload.k().get(1)).isEqualTo("x");
    }

    @Test
    void 기능이_더한_추가_필드는_보존된다() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("tab", "published");
        extra.put("vis", "public");
        ListScope scope = ListScope.of("manage:published:public");
        String cursor = codec.encode(scope, List.of(1790755200123456L, 9L), extra);
        assertThat(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8))
                .isEqualTo(
                        "{\"v\":1,\"l\":\"manage:published:public\",\"k\":[1790755200123456,9],"
                                + "\"tab\":\"published\",\"vis\":\"public\"}");
        CursorPayload payload = codec.decode(cursor, scope);
        assertThat(payload.extra())
                .containsEntry("tab", "published")
                .containsEntry("vis", "public");
    }

    @Test
    void 목록_구분이_다른_커서는_INVALID_CURSOR() {
        String blog = codec.encode(ListScope.blog("kim755030"), List.of(1L, 2L), Map.of());
        assertThatThrownBy(() -> codec.decode(blog, ListScope.home()))
                .isInstanceOf(InvalidCursorException.class)
                .satisfies(
                        e ->
                                assertThat(((InvalidCursorException) e).reasonCode())
                                        .isEqualTo(CommonReasonCode.INVALID_CURSOR));
        assertThatThrownBy(() -> codec.decode(blog, ListScope.blog("other")))
                .isInstanceOf(InvalidCursorException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "%%%not-base64%%%",
                "bm90IGpzb24", // "not json"
                "W10", // []
                "eyJ2IjoyLCJsIjoiaG9tZSIsImsiOlsxXX0", // {"v":2,"l":"home","k":[1]}
                "eyJ2IjoxLCJsIjoiaG9tZSJ9", // {"v":1,"l":"home"}
                "eyJ2IjoxLCJsIjoiaG9tZSIsImsiOjF9", // {"v":1,"l":"home","k":1}
                "eyJ2IjoxLCJsIjoiaG9tZSIsImsiOltdfQ", // {"v":1,"l":"home","k":[]}
                "eyJ2IjoiMSIsImwiOiJob21lIiwiayI6WzFdfQ", // {"v":"1","l":"home","k":[1]}
                "eyJ2IjoxLCJrIjpbMV19", // {"v":1,"k":[1]}
                ""
            })
    void 풀리지_않는_커서는_INVALID_CURSOR(String raw) {
        assertThatThrownBy(() -> codec.decode(raw, ListScope.home()))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void 패딩이_붙은_커서도_받지_않는다() {
        String padded = codec.encode(ListScope.home(), List.of(1L), Map.of()) + "==";
        assertThatThrownBy(() -> codec.decode(padded, ListScope.home()))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void 길이_512자를_넘는_커서는_INVALID_CURSOR() {
        String longKey = "x".repeat(600);
        String cursor = b64("{\"v\":1,\"l\":\"home\",\"k\":[\"" + longKey + "\"]}");
        assertThat(cursor.length()).isGreaterThan(512);
        assertThatThrownBy(() -> codec.decode(cursor, ListScope.home()))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void 키_안에_객체를_넣은_커서는_INVALID_CURSOR() {
        String cursor = b64("{\"v\":1,\"l\":\"home\",\"k\":[{\"id\":1}]}");
        assertThatThrownBy(() -> codec.decode(cursor, ListScope.home()))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void 목록_구분_값() {
        assertThat(ListScope.home().value()).isEqualTo("home");
        assertThat(ListScope.blog("kim755030").value()).isEqualTo("blog:kim755030");
        assertThat(ListScope.myFriends().value()).isEqualTo("me:friends");
        assertThat(ListScope.friendRequests().value()).isEqualTo("me:friend-requests");
        assertThat(ListScope.of("manage:trash")).isEqualTo(ListScope.of("manage:trash"));
        assertThatThrownBy(() -> ListScope.of(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
