package com.team.blog.post.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.account.application.policy.BannedWordFilter;
import com.team.blog.account.application.policy.ReservedWords;
import com.team.blog.post.application.exception.PublishValidationException;
import com.team.blog.post.config.PostAuthoringProperties;
import com.team.blog.post.domain.PrivateVisibilityRule;
import com.team.blog.post.domain.PublicVisibilityRule;
import com.team.blog.post.domain.PublishValidator;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.domain.VisibilityRegistry;
import com.team.blog.shared.error.FieldError;
import com.team.blog.tag.application.TagService;
import com.team.blog.tag.domain.TagNormalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 발행 검증: 실패 항목을 모두 모은다 (002 T036, FR-026, data-model §3, 05 §4). */
class PublishValidatorTest {

    private static final PostAuthoringProperties PROPS =
            new PostAuthoringProperties(
                    new PostAuthoringProperties.Autosave(
                            Duration.ofHours(24),
                            Duration.ofMinutes(1),
                            new PostAuthoringProperties.RateLimit(1, Duration.ofSeconds(5)),
                            1_048_576),
                    new PostAuthoringProperties.Post(10, 100, 100_000),
                    new PostAuthoringProperties.Publish(Duration.ofSeconds(600)),
                    new PostAuthoringProperties.Cleanup(
                            "0 30 3 * * *", "Asia/Seoul", Duration.ofHours(24)));

    /** 운영 금칙어 목록 (008 TagNormalizer — 단어는 테스트에 적지 않고 파일에서 읽는다). */
    private static final List<String> BANNED =
            List.copyOf(ReservedWords.readList(new ClassPathResource("policy/banned-words.txt")));

    private static final TagNormalizer TAG_NORMALIZER =
            new TagNormalizer(
                    new BannedWordFilter(
                            BANNED,
                            ReservedWords.readList(
                                    new ClassPathResource("policy/banned-words-exceptions.txt"))));

    private final PublishValidator validator =
            new PublishValidator(
                    PROPS,
                    new TagService(null, TAG_NORMALIZER),
                    new VisibilityRegistry(
                            List.of(new PublicVisibilityRule(), new PrivateVisibilityRule())));

    private static List<String> codes(PublishValidationException e) {
        return e.errors().stream().map(err -> err.field() + "/" + err.code()).toList();
    }

    private PublishValidationException fail(
            String title, String contentMd, List<String> tags, String visibility) {
        try {
            validator.validate(title, contentMd, tags, visibility);
        } catch (PublishValidationException e) {
            return e;
        }
        throw new AssertionError("검증이 통과했습니다");
    }

    @Test
    void 통과하면_정리한_제목과_공개_범위를_돌려준다() {
        PublishValidator.Validated ok =
                validator.validate(" ​JPA N+1 ", "## 문제\n본문", List.of("Spring", "jpa"), "PUBLIC");
        assertThat(ok.title()).isEqualTo("JPA N+1");
        assertThat(ok.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(ok.tags()).containsExactly("spring", "jpa");
    }

    @Test
    void 빈_제목과_업로드_대기_사진은_둘_다_오류다() {
        PublishValidationException e = fail("  ", "![대기](local:7f3e)", List.of(), "PUBLIC");
        assertThat(codes(e)).containsExactly("title/TITLE_REQUIRED", "contentMd/PENDING_IMAGES");
        assertThat(e.reasonCode().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(e.status().value()).isEqualTo(400);
    }

    @Test
    void 공백뿐인_본문은_CONTENT_REQUIRED() {
        assertThat(codes(fail("제목", " \n\t ", List.of(), "PUBLIC")))
                .containsExactly("contentMd/CONTENT_REQUIRED");
    }

    @Test
    void 본문_100001자는_CONTENT_TOO_LONG_100000자는_통과() {
        assertThat(codes(fail("제목", "가".repeat(100_001), List.of(), "PUBLIC")))
                .containsExactly("contentMd/CONTENT_TOO_LONG");
        validator.validate("제목", "가".repeat(100_000), List.of(), "PUBLIC");
    }

    @Test
    void 제목_101자는_TITLE_TOO_LONG() {
        assertThat(codes(fail("가".repeat(101), "본문", List.of(), "PUBLIC")))
                .containsExactly("title/TITLE_TOO_LONG");
    }

    @Test
    void 태그_11개는_TOO_MANY_TAGS_10개는_통과() {
        List<String> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add("tag" + i);
        }
        assertThat(codes(fail("제목", "본문", eleven, "PUBLIC"))).containsExactly("tags/TOO_MANY_TAGS");
        validator.validate("제목", "본문", eleven.subList(0, 10), "PUBLIC");
    }

    @Test
    void 대소문자_중복은_하나로_센다() {
        PublishValidator.Validated ok =
                validator.validate("제목", "본문", List.of("Java", " java ", "JAVA"), "PRIVATE");
        assertThat(ok.tags()).containsExactly("java");
    }

    @Test
    void 형식이_틀린_태그는_그_칸_번호로_INVALID_TAG() {
        PublishValidationException e =
                fail("제목", "본문", List.of("spring", "🔥hot", "  ", "a".repeat(31)), "PUBLIC");
        // 008: 31자는 형식 오류가 아니라 TAG_TOO_LONG (칸 번호 규칙은 그대로)
        assertThat(codes(e))
                .containsExactly(
                        "tags[1]/INVALID_TAG", "tags[2]/INVALID_TAG", "tags[3]/TAG_TOO_LONG");
    }

    @Test
    void 개수_초과와_칸_오류를_함께_모은다() {
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            tags.add("tag" + i);
        }
        tags.add(Character.toString(0x1F525) + "hot");
        assertThat(codes(fail("제목", "본문", tags, "PUBLIC")))
                .containsExactly("tags/TOO_MANY_TAGS", "tags[11]/INVALID_TAG");
    }

    @Test
    void 개수는_중복_제거_후에_센다() {
        List<String> same =
                List.of(
                        "Spring",
                        "spring",
                        "SPRING",
                        "#spring",
                        " spring ",
                        "##Spring",
                        "spring ",
                        "ｓｐｒｉｎｇ",
                        "#SPRING",
                        "Spring  ",
                        "spring");
        assertThat(same).hasSize(11);
        assertThat(validator.validate("제목", "본문", same, "PUBLIC").tags()).containsExactly("spring");
    }

    @Test
    void TAG_TOO_LONG_TAG_BANNED_WORD는_그_칸_번호로() {
        PublishValidationException e =
                fail(
                        "제목",
                        "본문",
                        List.of("ok", "b".repeat(31), "x" + BANNED.get(0), "Spring Boot"),
                        "PUBLIC");
        assertThat(codes(e)).containsExactly("tags[1]/TAG_TOO_LONG", "tags[2]/TAG_BANNED_WORD");
        assertThat(e.errors())
                .extracting(FieldError::message)
                .containsExactly("태그는 30자까지 쓸 수 있어요", "쓸 수 없는 단어가 들어 있어요")
                .noneMatch(message -> message.contains(BANNED.get(0)));
    }

    @Test
    void 정규화한_태그를_입력_순서대로_돌려준다() {
        PublishValidator.Validated ok =
                validator.validate(
                        "제목", "본문", List.of("Spring Boot", "#JPA", "spring-boot", "C#"), "PUBLIC");
        assertThat(ok.tags()).containsExactly("spring-boot", "jpa", "c#");
    }

    @Test
    void 등록되지_않은_공개_범위는_INVALID_VISIBILITY() {
        assertThat(codes(fail("제목", "본문", List.of(), "FRIENDS")))
                .containsExactly("visibility/INVALID_VISIBILITY");
        assertThat(codes(fail("제목", "본문", List.of(), null)))
                .containsExactly("visibility/INVALID_VISIBILITY");
    }

    @Test
    void 여러_항목이_틀리면_모두_모은다() {
        PublishValidationException e = fail("", "![대기](local:7f3e)", List.of("🔥"), "nope");
        assertThat(codes(e))
                .containsExactly(
                        "title/TITLE_REQUIRED",
                        "contentMd/PENDING_IMAGES",
                        "tags[0]/INVALID_TAG",
                        "visibility/INVALID_VISIBILITY");
        assertThat(e.errors()).extracting(FieldError::message).doesNotContainNull();
    }

    @Test
    void 참조형_local_사진도_업로드_대기로_본다() {
        assertThat(codes(fail("제목", "![a][x]\n\n[x]: local:abcd", List.of(), "PUBLIC")))
                .containsExactly("contentMd/PENDING_IMAGES");
    }

    @Test
    void local_글자만_있는_본문은_사진이_아니다() {
        validator.validate("제목", "주소 local:abc 는 글자예요", List.of(), "PUBLIC");
    }

    @Test
    void null_태그_목록은_빈_목록이다() {
        assertThat(validator.validate("제목", "본문", null, "PUBLIC").tags()).isEmpty();
    }

    @Test
    void 검증_예외는_칸_오류가_비면_만들_수_없다() {
        assertThatThrownBy(() -> new PublishValidationException(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
