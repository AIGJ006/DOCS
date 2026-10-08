package com.team.blog.discovery.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.application.search.PeopleQuery;
import com.team.blog.discovery.application.search.SearchProperties;
import com.team.blog.discovery.application.search.SearchQuery;
import com.team.blog.discovery.application.search.SearchQueryParser;
import com.team.blog.discovery.application.search.SearchWord;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 검색어 정리 (012 T006, contracts §4·§7, research R6·R10, FR-018·FR-024·FR-036). */
class SearchQueryParserTest {

    private final SearchQueryParser parser =
            new SearchQueryParser(
                    new SearchProperties(
                            50,
                            5,
                            3000,
                            40,
                            20,
                            new SearchProperties.RateLimit(30, Duration.ofMinutes(1))));

    private List<String> words(String raw) {
        return parser.parse(raw).words().stream().map(SearchWord::text).toList();
    }

    @Test
    void 표_트랜잭션_정리_a() {
        SearchQuery q = parser.parse("트랜잭션 정리 a");
        assertThat(words("트랜잭션 정리 a")).containsExactly("트랜잭션", "정리");
        assertThat(q.hasTwoCharWord()).isTrue();
        assertThat(q.words().get(0).inContent()).isTrue();
        assertThat(q.words().get(1).inContent()).isFalse();
    }

    @Test
    void 표_롬복_두글자() {
        SearchQuery q = parser.parse("롬복");
        assertThat(words("롬복")).containsExactly("롬복");
        assertThat(q.hasTwoCharWord()).isTrue();
    }

    @Test
    void 표_한글자만_남는단어_없음() {
        assertThat(parser.parse("a b c").isEmpty()).isTrue();
        assertThat(parser.parse("").isEmpty()).isTrue();
        assertThat(parser.parse(null).isEmpty()).isTrue();
        assertThat(parser.parse("   　 ").isEmpty()).isTrue();
    }

    @Test
    void 표_특수문자는_글자_그대로() {
        assertThat(words("100% 할인")).containsExactly("100%", "할인");
        SearchQuery snake = parser.parse("snake_case");
        assertThat(words("snake_case")).containsExactly("snake_case");
        assertThat(snake.hasTwoCharWord()).isFalse();
        assertThat(words("a\\b")).containsExactly("a\\b");
        assertThat(words("#spring")).containsExactly("#spring");
    }

    @Test
    void 여섯_단어면_앞_5단어() {
        assertThat(words("일일 이이 삼삼 사사 오오 육육")).containsExactly("일일", "이이", "삼삼", "사사", "오오");
    }

    @Test
    void 오십자_넘으면_자른_뒤_나눈다() {
        String raw = "가".repeat(49) + " 나나";
        // 50 코드 포인트까지: 가×49 + 공백 → 단어 하나
        assertThat(words(raw)).containsExactly("가".repeat(49));
        String sixty = "a".repeat(60);
        assertThat(words(sixty)).containsExactly("a".repeat(50));
    }

    @Test
    void NFC_조합형으로_바꾼다() {
        String decomposed = "트랜"; // 트랜 (자모 분해)
        assertThat(words(decomposed)).containsExactly("트랜");
    }

    @Test
    void 유니코드_공백으로도_나눈다() {
        assertThat(words("트랜잭션　정리 스프링\t자바")).containsExactly("트랜잭션", "정리", "스프링", "자바");
        assertThat(words("  트랜잭션  ")).containsExactly("트랜잭션");
    }

    @Test
    void 서로게이트_쌍은_한_글자로_센다() {
        // 😀는 코드 포인트 1개 → 1글자 단어로 버린다. 😀😀는 2글자
        assertThat(parser.parse("😀").isEmpty()).isTrue();
        SearchQuery q = parser.parse("😀😀");
        assertThat(q.words()).hasSize(1);
        assertThat(q.words().get(0).length()).isEqualTo(2);
        // 50 코드 포인트 경계에서 쌍을 가르지 않는다
        String raw = "😀".repeat(60);
        String only = parser.parse(raw).words().get(0).text();
        assertThat(only.codePointCount(0, only.length())).isEqualTo(50);
        assertThat(Character.isHighSurrogate(only.charAt(only.length() - 1))).isFalse();
    }

    @Test
    void 가장_긴_단어는_같으면_앞_단어() {
        assertThat(parser.parse("스프링 트랜잭션 격리수준").longest().text()).isEqualTo("트랜잭션");
        assertThat(parser.parse("abc def").longest().text()).isEqualTo("abc");
    }

    @Test
    void 지문은_단어가_같으면_같고_다르면_다르다() {
        String a = parser.parse("트랜잭션  정리").fingerprint();
        assertThat(a).hasSize(16).matches("[0-9a-f]{16}");
        assertThat(parser.parse(" 트랜잭션 정리 a").fingerprint()).isEqualTo(a);
        assertThat(parser.parse("정리 트랜잭션").fingerprint()).isNotEqualTo(a);
        assertThat(parser.parse("트랜잭션정리").fingerprint()).isNotEqualTo(a);
    }

    @Test
    void 사람_검색어는_공백과_맨앞_골뱅이를_지운_한_덩어리() {
        assertThat(parser.parsePeople("김 민서")).contains(new PeopleQuery("김민서"));
        assertThat(parser.parsePeople("@kim7550")).contains(new PeopleQuery("kim7550"));
        assertThat(parser.parsePeople(" @ kim 7550 ")).contains(new PeopleQuery("kim7550"));
        assertThat(parser.parsePeople("@@kim")).contains(new PeopleQuery("@kim"));
        assertThat(parser.parsePeople("김")).isEqualTo(Optional.empty());
        assertThat(parser.parsePeople("@k")).isEqualTo(Optional.empty());
        assertThat(parser.parsePeople(null)).isEqualTo(Optional.empty());
        assertThat(parser.parsePeople("100%")).contains(new PeopleQuery("100%"));
    }
}
