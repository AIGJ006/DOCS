package com.team.blog.tag.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.tag.application.suggest.CleanedInput;
import com.team.blog.tag.application.suggest.SuggestInputCleaner;
import java.text.Normalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 입력 정리 (013 T009, contracts/providers.md §1, research R5). 결과 = 제목 + 본문 글자, 공백 하나, NFC, 대소문자 그대로.
 */
class SuggestInputCleanerTest {

    private final SuggestInputCleaner cleaner = new SuggestInputCleaner();

    private String body(String md) {
        return cleaner.clean("", md).text();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '\'',
            value = {
                "'# 제목\\n**굵게** _기울임_ ~~취소~~' | '제목 굵게 기울임 취소'",
                "'- 항목1\\n- 항목2\\n> 인용' | '항목1 항목2 인용'",
                "'![그림 설명](https://example.com/a.png)' | ''",
                "'[스프링 문서](https://spring.io)' | '스프링 문서'",
                "'`@Transactional`' | '@Transactional'",
                "'<div>html</div>' | ''",
                "'앞 <b>굵게</b> 뒤' | '앞 굵게 뒤'",
                "'| a | b |\\n|---|---|\\n| c | d |' | 'a b c d'",
                "'1. 하나\\n2. 둘' | '하나 둘'",
            })
    void providers_1절_표(String md, String expected) {
        assertThat(body(md.replace("\\n", "\n"))).isEqualTo(expected);
    }

    @Test
    void 코드_블록은_언어_이름과_앞_5줄() {
        String md = "```java\nl1\nl2\nl3\nl4\nl5\nl6\nl7\n```";
        assertThat(body(md)).isEqualTo("java l1 l2 l3 l4 l5");
    }

    @Test
    void 언어_없는_코드_블록과_들여쓴_코드도_앞_5줄() {
        assertThat(body("```\na\nb\nc\nd\ne\nf\n```")).isEqualTo("a b c d e");
        assertThat(body("문단\n\n    x1\n    x2\n    x3\n    x4\n    x5\n    x6\n"))
                .isEqualTo("문단 x1 x2 x3 x4 x5");
    }

    @Test
    void 제목과_본문을_줄바꿈으로_잇고_공백은_하나로() {
        CleanedInput input = cleaner.clean("  JPA   N+1 ", "첫 문단\n\n\n둘째   문단\t끝");
        assertThat(input.text()).isEqualTo("JPA N+1 첫 문단 둘째 문단 끝");
    }

    @Test
    void 대소문자는_그대로() {
        assertThat(cleaner.clean("Spring Boot", "JPA와 Hibernate").text())
                .isEqualTo("Spring Boot JPA와 Hibernate");
    }

    @Test
    void NFC로_조합형_한글을_합친다() {
        String decomposed = Normalizer.normalize("한글 태그", Normalizer.Form.NFD);
        assertThat(decomposed).isNotEqualTo("한글 태그");
        assertThat(cleaner.clean(decomposed, "").text()).isEqualTo("한글 태그");
    }

    @Test
    void 길이는_코드_포인트() {
        CleanedInput input = cleaner.clean("😀", "가a");
        assertThat(input.text()).isEqualTo("😀 가a");
        assertThat(input.length()).isEqualTo(4);
    }

    @Test
    void 빈_값과_null() {
        assertThat(cleaner.clean(null, null).text()).isEmpty();
        assertThat(cleaner.clean("", "   ").length()).isZero();
    }

    @Test
    void 자르기는_코드_포인트_단위() {
        CleanedInput input = CleanedInput.of("😀😀😀abc");
        assertThat(input.truncate(2).text()).isEqualTo("😀😀");
        assertThat(input.truncate(2).length()).isEqualTo(2);
        assertThat(input.truncate(100)).isSameAs(input);
        assertThat(input.sha256()).hasSize(64).isEqualTo(CleanedInput.of("😀😀😀abc").sha256());
    }
}
