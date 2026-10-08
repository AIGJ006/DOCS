package com.team.blog.shared.text;

/**
 * 보이지 않는 글자 판정 (docs/12 §7-4, 008 research R2). 화면을 속이는 폭 0 글자·방향 제어 문자·제어 문자의 한 목록이다. 002 제목
 * 정리({@code TitleNormalizer})와 008 태그 정규화({@code TagNormalizer})가 함께 쓴다 — 같은 화면에서 제목과 태그가 다른 목록으로
 * 글자를 지우지 않게 한 곳에 둔다. 007 등 다른 기능도 이것을 쓴다.
 *
 * <ul>
 *   <li>U+200B~200F (폭 0 공백·연결자·방향 표시)
 *   <li>U+2060~2069 (단어 연결자·보이지 않는 연산자·방향 격리)
 *   <li>U+FEFF (BOM)
 *   <li>U+202A~202E (방향 덮어쓰기)
 *   <li>ISO 제어 문자 U+0000~001F, U+007F~009F
 * </ul>
 *
 * 공백(U+0020·U+00A0·U+3000)과 한글 자모는 대상이 아니다.
 */
public final class InvisibleCharacters {

    private InvisibleCharacters() {}

    /** 지워야 할 글자인가. */
    public static boolean isInvisible(int codePoint) {
        return (codePoint >= 0x200B && codePoint <= 0x200F)
                || (codePoint >= 0x2060 && codePoint <= 0x2069)
                || codePoint == 0xFEFF
                || (codePoint >= 0x202A && codePoint <= 0x202E)
                || Character.isISOControl(codePoint);
    }

    /** 보이지 않는 글자를 모두 지운다 ({@code null}이면 빈 문자열). 앞뒤 공백은 그대로 둔다. */
    public static String strip(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().filter(cp -> !isInvisible(cp)).forEach(out::appendCodePoint);
        return out.toString();
    }
}
