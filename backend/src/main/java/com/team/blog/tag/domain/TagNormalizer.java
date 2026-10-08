package com.team.blog.tag.domain;

import com.team.blog.account.application.policy.BannedWordFilter;
import com.team.blog.shared.text.InvisibleCharacters;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 태그 정규화 — 이 서비스의 태그 규칙은 이 클래스 하나다 (008 contracts/normalization.md §1, research R1·R2, C-TAG-1).
 *
 * <ol>
 *   <li>① NFKC (전각 → 반각, 호환 문자)
 *   <li>② 보이지 않는 글자·방향 제어·제어 문자 제거 ({@link InvisibleCharacters} — 002 제목 정리와 같은 목록). ⑥보다 먼저라 탭·줄바꿈은
 *       {@code -}가 아니라 지워진다
 *   <li>③ 앞뒤 공백 제거 ({@link String#strip()})
 *   <li>④ 맨 앞 {@code #} 모두 제거 → 다시 ③
 *   <li>⑤ 소문자 ({@link Locale#ROOT} — 터키어 설정에서도 {@code I → i})
 *   <li>⑥ 공백 묶음 → {@code -} 하나 ({@code \p{IsWhite_Space}})
 *   <li>⑦ {@code -} 연속 → 하나, 처음·끝 {@code -} 제거
 *   <li>⑧ 형식: 허용 문자만 + 한글·영문·숫자 1자 이상 → 아니면 {@code INVALID_TAG}, 코드 포인트 30 초과 → {@code
 *       TAG_TOO_LONG}
 *   <li>⑨ 금칙어 (001 {@link BannedWordFilter}) → {@code TAG_BANNED_WORD}. {@link #normalize}만 한다
 * </ol>
 *
 * 한 칸에 문제가 여럿이면 {@code INVALID_TAG → TAG_TOO_LONG → TAG_BANNED_WORD} 순서로 하나만 돌려준다. 금칙어 거부는 어떤 단어인지
 * 결과·로그에 남기지 않는다(로그 자체를 남기지 않는다).
 *
 * <p>진입점: {@link #normalize}(①~⑨) — 발행·013 AI 추천. {@link #normalizeQuery}(①~⑧) — 자동완성 검색어·태그 주소·블로그
 * 필터·012 검색창 {@code #태그}. 화면 {@code features/tag/normalizeTag.ts}가 같은 규칙(⑨ 제외)을 따르며, 두 구현은 같은 예시
 * 표({@code tag/normalization-cases.csv})로 시험한다.
 */
@Component
public class TagNormalizer {

    /** 허용 문자 한 글자 (V1 {@code ck_tag_name}과 같은 문자 집합). */
    private static final String CHARACTER = "[가-힣a-z0-9._+#-]";

    /** V1 {@code ck_tag_name} 앞 조건과 같은 문구: 허용 문자 1~30자. */
    public static final Pattern ALLOWED = Pattern.compile("^" + CHARACTER + "{1,30}$");

    /** V1 {@code ck_tag_name} 뒤 조건과 같은 문구: 한글·영문 소문자·숫자 하나 이상. */
    public static final Pattern HAS_WORD = Pattern.compile("[가-힣a-z0-9]");

    /** 정규화된 이름의 최대 코드 포인트 수 (V1 {@code varchar(30)}). */
    public static final int MAX_LENGTH = 30;

    private static final Pattern ONLY_ALLOWED_CHARACTERS = Pattern.compile(CHARACTER + "+");
    private static final Pattern LEADING_SHARPS = Pattern.compile("^#+");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\p{IsWhite_Space}+");
    private static final Pattern HYPHEN_RUN = Pattern.compile("-{2,}");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-|-$");

    private final BannedWordFilter bannedWordFilter;

    public TagNormalizer(BannedWordFilter bannedWordFilter) {
        this.bannedWordFilter = bannedWordFilter;
    }

    /** ①~⑨ 전체. 발행 검증·013 AI 추천이 쓴다. */
    public TagNormalization normalize(String raw) {
        String name = clean(raw);
        TagReasonCode format = formatViolation(name);
        if (format != null) {
            return new TagNormalization.Rejected(format);
        }
        if (bannedWordFilter.containsBanned(name)) {
            return new TagNormalization.Rejected(TagReasonCode.TAG_BANNED_WORD);
        }
        return new TagNormalization.Accepted(name);
    }

    /**
     * ①~⑧ (금칙어 검사 없음, 길이 초과도 실패). 검색어·주소 값용이다. 실패하면 빈 값.
     *
     * <p>금칙어를 거르지 않는 이유: 자동완성·주소는 이미 저장된 태그를 찾을 뿐이고, 금칙어 태그는 만들어질 수 없으므로 결과는 항상 빈 목록이다(spec Edge
     * Cases).
     */
    public Optional<String> normalizeQuery(String raw) {
        String name = clean(raw);
        return formatViolation(name) == null ? Optional.of(name) : Optional.empty();
    }

    /** ①~⑦. */
    private static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC); // ①
        s = InvisibleCharacters.strip(s); // ②
        s = s.strip(); // ③
        s = LEADING_SHARPS.matcher(s).replaceFirst("").strip(); // ④
        s = s.toLowerCase(Locale.ROOT); // ⑤
        s = WHITESPACE_RUN.matcher(s).replaceAll("-"); // ⑥
        s = HYPHEN_RUN.matcher(s).replaceAll("-"); // ⑦
        return EDGE_HYPHENS.matcher(s).replaceAll("");
    }

    /** ⑧. 통과하면 {@code null}. */
    private static TagReasonCode formatViolation(String name) {
        if (name.isEmpty()
                || !ONLY_ALLOWED_CHARACTERS.matcher(name).matches()
                || !HAS_WORD.matcher(name).find()) {
            return TagReasonCode.INVALID_TAG;
        }
        if (name.codePointCount(0, name.length()) > MAX_LENGTH) {
            return TagReasonCode.TAG_TOO_LONG;
        }
        return null;
    }
}
