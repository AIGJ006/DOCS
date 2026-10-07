package com.team.blog.post.domain;

/**
 * 빈 임시글 판정 (002 research B-9, 006 13 D-2와 공용). 공백 문자 집합 {@link #WHITESPACE}로 앞뒤를 지운 제목·본문이 둘 다 빈
 * 문자열이면 빈 글이다.
 *
 * <p>배치 SQL은 같은 상수를 바인딩해 {@code btrim(title, :ws)}로 같은 기준을 쓴다({@link #SQL_IS_EMPTY}). 인자 없는 {@code
 * btrim}은 공백(U+0020)만 지우므로 쓰지 않는다. 전각 공백·폭 0 공백은 내용으로 본다(SQL과 같게).
 */
public final class EmptyDraftPolicy {

    /** 빈 글 판정에서 지우는 문자: 공백, 탭, CR, LF. */
    public static final String WHITESPACE = " \t\r\n";

    /** 배치 SQL 조건 (별칭 {@code p} = {@code post}, 파라미터 {@code :ws} = {@link #sqlWhitespace()}). */
    public static final String SQL_IS_EMPTY =
            "btrim(p.title, :ws) = '' AND btrim(p.content_md, :ws) = ''";

    private EmptyDraftPolicy() {}

    /** 제목·본문이 모두 비었는가. {@code null}은 빈 값으로 본다. */
    public static boolean isEmpty(String title, String contentMd) {
        return isBlank(title) && isBlank(contentMd);
    }

    /** SQL {@code :ws} 파라미터 값 ({@link #WHITESPACE}와 같음). */
    public static String sqlWhitespace() {
        return WHITESPACE;
    }

    private static boolean isBlank(String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (WHITESPACE.indexOf(s.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
