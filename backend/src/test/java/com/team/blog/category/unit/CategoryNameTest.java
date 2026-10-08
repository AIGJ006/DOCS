package com.team.blog.category.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.category.domain.CategoryName;
import com.team.blog.shared.error.ValidationException;
import org.junit.jupiter.api.Test;

/** 017 FR-003·FR-004 이름 정리·키. */
class CategoryNameTest {

    @Test
    void 앞뒤_공백을_지우고_안쪽_공백은_한_칸() {
        assertThat(CategoryName.of("  개발   일기\t메모  ").value()).isEqualTo("개발 일기 메모");
        assertThat(CategoryName.of("　Spring　Boot　").value()).isEqualTo("Spring Boot");
    }

    @Test
    void 키는_소문자() {
        assertThat(CategoryName.of("Spring BOOT").key()).isEqualTo("spring boot");
        assertThat(CategoryName.of("TITLE").key()).isEqualTo("title");
    }

    @Test
    void 조합형_한글은_완성형으로() {
        String decomposed = "가"; // ㄱ + ㅏ
        assertThat(CategoryName.of(decomposed).value()).isEqualTo("가");
    }

    @Test
    void 빈_이름과_31자는_칸_오류() {
        assertThatThrownBy(() -> CategoryName.of("   "))
                .isInstanceOf(ValidationException.class)
                .satisfies(
                        e ->
                                assertThat(((ValidationException) e).errors().get(0).code())
                                        .isEqualTo("CATEGORY_NAME_REQUIRED"));
        assertThatThrownBy(() -> CategoryName.of(null)).isInstanceOf(ValidationException.class);
        assertThat(CategoryName.of("가".repeat(30)).value()).hasSize(30);
        assertThatThrownBy(() -> CategoryName.of("가".repeat(31)))
                .satisfies(
                        e ->
                                assertThat(((ValidationException) e).errors().get(0).code())
                                        .isEqualTo("CATEGORY_NAME_TOO_LONG"));
    }

    @Test
    void 이모지는_코드_포인트로_센다() {
        assertThat(CategoryName.of("😀".repeat(30)).value()).isNotEmpty();
    }
}
