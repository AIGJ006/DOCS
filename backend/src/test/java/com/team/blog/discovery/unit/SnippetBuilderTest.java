package com.team.blog.discovery.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.application.search.SearchProperties;
import com.team.blog.discovery.application.search.SearchQuery;
import com.team.blog.discovery.application.search.SearchQueryParser;
import com.team.blog.discovery.application.search.Snippet;
import com.team.blog.discovery.application.search.SnippetBuilder;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 주변 문장 (012 T009, contracts §6, research R9, FR-033·FR-034). */
class SnippetBuilderTest {

    private final SearchProperties properties =
            new SearchProperties(
                    50, 5, 3000, 40, 20, new SearchProperties.RateLimit(30, Duration.ofMinutes(1)));
    private final SearchQueryParser parser = new SearchQueryParser(properties);
    private final SnippetBuilder builder = new SnippetBuilder(properties);

    private Snippet build(String title, String content, String excerpt, String q) {
        SearchQuery query = parser.parse(q);
        return builder.build(title, content, excerpt, query.words());
    }

    private static List<String> marked(Snippet snippet) {
        return snippet.marks().stream().map(m -> snippet.text().substring(m[0], m[1])).toList();
    }

    @Test
    void 본문에서_처음_나온_곳_앞뒤_40자와_말줄임() {
        String content = "가".repeat(100) + "트랜잭션" + "나".repeat(100);
        Snippet s = build("제목", content, "요약", "트랜잭");

        assertThat(s.text()).isEqualTo("…" + "가".repeat(40) + "트랜잭" + "션" + "나".repeat(39) + "…");
        assertThat(marked(s)).containsExactly("트랜잭");
        assertThat(s.marks().get(0)).containsExactly(41, 44);
    }

    @Test
    void 짧은_본문은_말줄임_없이_전부() {
        Snippet s = build("제목", "스프링 트랜잭션 정리", null, "트랜잭션");
        assertThat(s.text()).isEqualTo("스프링 트랜잭션 정리");
        assertThat(s.marks()).hasSize(1);
        assertThat(s.marks().get(0)).containsExactly(4, 8);
    }

    @Test
    void 본문에_없으면_제목에서() {
        Snippet s = build("롬복 사용기", "다른 이야기", "요약", "롬복");
        assertThat(s.text()).isEqualTo("롬복 사용기");
        assertThat(marked(s)).containsExactly("롬복");
    }

    @Test
    void 둘_다_없으면_excerpt_강조_없음() {
        Snippet s = build("제목입니다", "본문입니다", "요약입니다", "spring");
        assertThat(s.text()).isEqualTo("요약입니다");
        assertThat(s.marks()).isEmpty();
        assertThat(build("제목입니다", "본문입니다", null, "spring").text()).isEmpty();
    }

    @Test
    void 두글자_단어는_본문에서_찾지_않고_제목에서() {
        // "정리"는 2글자 → 본문 강조 대상이 아니다(FR-019). 제목에서 찾는다
        Snippet s = build("트랜잭션 정리", "본문에도 정리가 있다", null, "정리");
        assertThat(s.text()).isEqualTo("트랜잭션 정리");
        assertThat(marked(s)).containsExactly("정리");
    }

    @Test
    void 잘라_낸_글자_안의_모든_단어_위치를_강조하고_겹치면_합친다() {
        Snippet s = build("제목", "abcabc 그리고 bcab", null, "abc bcab");
        // abc(0-3), abc(3-6) → [0,6] 합침, bcab는 1-5, 4-? ... 모두 합쳐 오름차순·겹침 없음
        for (int i = 1; i < s.marks().size(); i++) {
            assertThat(s.marks().get(i)[0]).isGreaterThan(s.marks().get(i - 1)[1]);
        }
        assertThat(marked(s)).containsExactly("abcabc", "bcab");
    }

    @Test
    void 대소문자_무시와_길이가_바뀌는_글자() {
        Snippet s = build("제목", "Welcome to İstanbul and ISTANBUL", null, "istanbul");
        assertThat(marked(s)).containsExactly("İstanbul", "ISTANBUL");
        Snippet spring = build("제목", "Spring SPRING spring", null, "sPrInG");
        assertThat(spring.marks()).hasSize(3);
    }

    @Test
    void 서로게이트_쌍을_가르지_않는다() {
        String content = "😀".repeat(50) + "트랜잭션" + "😀".repeat(50);
        Snippet s = build("제목", content, null, "트랜잭션");
        String inner = s.text().substring(1, s.text().length() - 1);
        assertThat(inner.codePointCount(0, inner.length())).isEqualTo(40 + 4 + 40);
        assertThat(Character.isLowSurrogate(inner.charAt(0))).isFalse();
        assertThat(Character.isHighSurrogate(inner.charAt(inner.length() - 1))).isFalse();
        assertThat(marked(s)).containsExactly("트랜잭션");
    }

    @Test
    void 줄바꿈은_공백_하나로() {
        Snippet s = build("제목", "첫 줄\r\n트랜잭션\n셋째\r줄", null, "트랜잭션");
        assertThat(s.text()).isEqualTo("첫 줄 트랜잭션 셋째 줄");
        assertThat(marked(s)).containsExactly("트랜잭션");
    }

    @Test
    void 스크립트와_서식_기호는_글자_그대로() {
        Snippet s = build("제목", "**굵게** <script>alert(1)</script> 트랜잭션", null, "트랜잭션");
        assertThat(s.text()).isEqualTo("**굵게** <script>alert(1)</script> 트랜잭션");
        assertThat(marked(s)).containsExactly("트랜잭션");
    }

    @Test
    void 특수문자_단어도_글자_그대로_찾는다() {
        assertThat(marked(build("제목", "오늘 100% 할인", null, "100%"))).containsExactly("100%");
        assertThat(marked(build("제목", "a\\b 경로", null, "a\\b"))).containsExactly("a\\b");
        assertThat(marked(build("제목", "use snake_case here", null, "snake_case")))
                .containsExactly("snake_case");
    }

    @Test
    void 여러_단어_중_가장_앞에_나온_곳이_기준() {
        String content = "x".repeat(60) + "격리수준" + "y".repeat(10) + "트랜잭션";
        Snippet s = build("제목", content, null, "트랜잭션 격리수준");
        assertThat(s.text()).startsWith("…" + "x".repeat(40) + "격리수준");
        assertThat(marked(s)).containsExactly("격리수준", "트랜잭션");
    }
}
