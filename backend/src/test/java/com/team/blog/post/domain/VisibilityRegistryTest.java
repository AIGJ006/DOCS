package com.team.blog.post.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.post.infra.PostView;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.security.Viewer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/** 공개 범위 허용값 = 등록된 {@link VisibilityRule} Bean의 값 집합 (research R-03·R-21, FR-001·FR-020·FR-049). */
class VisibilityRegistryTest {

    private final VisibilityRegistry registry =
            new VisibilityRegistry(
                    List.of(new PublicVisibilityRule(), new PrivateVisibilityRule()));

    @Test
    void 공통_구성의_허용값은_PUBLIC과_PRIVATE뿐이다() {
        assertThat(registry.allowedValues())
                .containsExactlyInAnyOrder(Visibility.PUBLIC, Visibility.PRIVATE);
        assertThat(registry.rule(Visibility.PUBLIC)).isInstanceOf(PublicVisibilityRule.class);
        assertThat(registry.rule(Visibility.PRIVATE)).isInstanceOf(PrivateVisibilityRule.class);
    }

    @Test
    void 허용값_문자열은_같은_이름의_enum으로_바뀐다() {
        assertThat(registry.require("PUBLIC", "visibility")).isEqualTo(Visibility.PUBLIC);
        assertThat(registry.require("PRIVATE", "defaultVisibility")).isEqualTo(Visibility.PRIVATE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FRIENDS", "PROTECTED", "public", "", " PUBLIC", "PUBLIC "})
    @NullSource
    void 허용_집합_밖의_값은_INVALID_VISIBILITY(String raw) {
        assertThatThrownBy(() -> registry.require(raw, "visibility"))
                .isInstanceOfSatisfying(
                        InvalidVisibilityException.class,
                        e -> {
                            assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(e.reasonCode().code()).isEqualTo("INVALID_VISIBILITY");
                            assertThat(e.getMessage()).isEqualTo("공개 범위를 다시 선택해 주세요");
                            assertThat(e.errors())
                                    .containsExactly(
                                            new FieldError(
                                                    "visibility",
                                                    "INVALID_VISIBILITY",
                                                    "허용되지 않은 공개 범위예요"));
                            assertThat(e.details()).isNull();
                        });
    }

    @Test
    void 오류의_칸_이름은_넘긴_필드_이름이다() {
        assertThatThrownBy(() -> registry.require("FRIENDS", "defaultVisibility"))
                .isInstanceOfSatisfying(
                        InvalidVisibilityException.class,
                        e -> assertThat(e.errors().get(0).field()).isEqualTo("defaultVisibility"));
    }

    @Test
    void 같은_값의_규칙이_둘이면_만들_수_없다() {
        VisibilityRule another =
                new VisibilityRule() {
                    @Override
                    public Visibility visibility() {
                        return Visibility.PUBLIC;
                    }

                    @Override
                    public boolean canRead(PostView post, Viewer viewer) {
                        return true;
                    }

                    @Override
                    public ListCondition listCondition(Viewer viewer, Long authorId) {
                        return ListCondition.excluded();
                    }
                };
        assertThatThrownBy(
                        () ->
                                new VisibilityRegistry(
                                        List.of(
                                                new PublicVisibilityRule(),
                                                new PrivateVisibilityRule(),
                                                another)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PUBLIC");
    }

    @Test
    void 등록되지_않은_값의_규칙을_찾으면_예외() {
        VisibilityRegistry onlyPublic = new VisibilityRegistry(List.of(new PublicVisibilityRule()));
        assertThat(onlyPublic.allowedValues()).containsExactly(Visibility.PUBLIC);
        assertThatThrownBy(() -> onlyPublic.require("PRIVATE", "visibility"))
                .isInstanceOf(InvalidVisibilityException.class);
        assertThatThrownBy(() -> onlyPublic.rule(Visibility.PRIVATE))
                .isInstanceOf(IllegalStateException.class);
    }
}
