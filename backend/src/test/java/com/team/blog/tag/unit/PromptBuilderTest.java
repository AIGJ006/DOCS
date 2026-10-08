package com.team.blog.tag.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.tag.application.suggest.Provider;
import com.team.blog.tag.application.suggest.TagSuggestInput;
import com.team.blog.tag.infra.ai.PromptBuilder;
import com.team.blog.tag.infra.ai.PromptBuilder.Prompt;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 프롬프트 (013 T013, contracts/providers.md §2). */
class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    private static final List<String> POPULAR = List.of("spring", "react", "spring-boot", "jpa");

    private static TagSuggestInput input(String text, List<String> current) {
        return new TagSuggestInput(text, false, POPULAR, current);
    }

    @Test
    void 순서는_지시문_인기_태그_붙인_태그_본문() {
        Prompt p = builder.build(Provider.GEMINI, input("JPA N+1 정리 본문", List.of("jpa")));
        assertThat(p.system()).startsWith("너는 기술 블로그 글에 붙일 태그를 고른다.");
        assertThat(p.system()).contains("{\"tags\": [...]} JSON으로만 답한다.");
        assertThat(p.user())
                .isEqualTo(
                        "인기 태그: spring, react, spring-boot, jpa\n"
                                + "이미 붙인 태그: jpa\n"
                                + "제목과 본문:\n"
                                + "JPA N+1 정리 본문");
    }

    @Test
    void 붙인_태그가_없으면_없음() {
        Prompt p = builder.build(Provider.GEMINI, input("본문", List.of()));
        assertThat(p.user()).contains("\n이미 붙인 태그: 없음\n");
    }

    @Test
    void Ollama는_입력에_나오는_인기_태그만() {
        Prompt p = builder.build(Provider.OLLAMA, input("Spring Boot에서 JPA 지연 로딩", List.of()));
        assertThat(p.user()).startsWith("인기 태그: spring, spring-boot, jpa\n");
        Prompt none = builder.build(Provider.OLLAMA, input("파이썬 이야기", List.of()));
        assertThat(none.user()).startsWith("인기 태그: 없음\n");
    }

    @Test
    void 회원_정보_필드가_없다() {
        assertThat(TagSuggestInput.class.getRecordComponents())
                .extracting(c -> c.getName())
                .containsExactly("text", "truncated", "popularTags", "currentTags");
        Prompt p = builder.build(Provider.GEMINI, input("본문", List.of()));
        assertThat(p.system() + p.user()).doesNotContain("닉네임", "이메일", "회원");
    }

    @Test
    void 같은_입력이면_같은_문자열() {
        assertThat(builder.build(Provider.GEMINI, input("본문 글자", List.of("a"))))
                .isEqualTo(builder.build(Provider.GEMINI, input("본문 글자", List.of("a"))));
    }
}
