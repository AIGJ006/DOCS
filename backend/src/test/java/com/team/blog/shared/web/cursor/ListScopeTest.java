package com.team.blog.shared.web.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.shared.error.CommonReasonCode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 목록 구분 값 (001 T021, 008 T007 — 태그 목록 {@code tag:{name}}, 블로그 태그 필터 {@code blog:{h}:tag:{name}}).
 */
class ListScopeTest {

    private final CursorCodec codec = new CursorCodec();

    @Test
    void 태그_목록과_블로그_태그_필터_값() {
        assertThat(ListScope.tag("c#").value()).isEqualTo("tag:c#");
        assertThat(ListScope.tag("스프링-부트").value()).isEqualTo("tag:스프링-부트");
        assertThat(ListScope.blogTag("kim", "jpa").value()).isEqualTo("blog:kim:tag:jpa");
    }

    @Test
    void 태그_목록_커서는_다른_목록에서_쓰면_INVALID_CURSOR() {
        String cursor = codec.encode(ListScope.tag("c#"), List.of(1L, 2L), null);

        assertThat(codec.decode(cursor, ListScope.tag("c#")).l()).isEqualTo("tag:c#");
        for (ListScope other :
                List.of(
                        ListScope.home(),
                        ListScope.blog("kim"),
                        ListScope.tag("c++"),
                        ListScope.blogTag("kim", "c#"))) {
            assertThatThrownBy(() -> codec.decode(cursor, other))
                    .as(other.value())
                    .isInstanceOfSatisfying(
                            InvalidCursorException.class,
                            e -> {
                                assertThat(e.reasonCode())
                                        .isEqualTo(CommonReasonCode.INVALID_CURSOR);
                                assertThat(e.reasonCode().status().value()).isEqualTo(400);
                            });
        }
    }

    @Test
    void 블로그_태그_필터_커서는_필터_없는_블로그_목록에서_쓸_수_없다() {
        String cursor = codec.encode(ListScope.blogTag("kim", "jpa"), List.of(1L, 2L), null);
        assertThatThrownBy(() -> codec.decode(cursor, ListScope.blog("kim")))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> codec.decode(cursor, ListScope.tag("jpa")))
                .isInstanceOf(InvalidCursorException.class);
    }
}
