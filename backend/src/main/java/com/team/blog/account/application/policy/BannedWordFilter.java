package com.team.blog.account.application.policy;

import java.util.Collection;

/**
 * 금칙어 필터 (FR-025, 09 §4, N-5, R-17). 닉네임·블로그 주소·소개가 함께 쓴다.
 *
 * <p>소문자로 바꾼 뒤 변형 4가지(그대로·숫자 제거·숫자→영문·1→l) 중 하나라도 금칙어를 포함하면 걸린다. 각 변형에서 예외 단어({@code 시발점} 등)를 먼저
 * 지운다. 결과는 참·거짓뿐이다 — 걸린 단어를 돌려주거나 로그에 남기지 않는다.
 */
public class BannedWordFilter {

    private final VariantMatcher matcher;

    public BannedWordFilter(Collection<String> bannedWords, Collection<String> exceptions) {
        this.matcher = new VariantMatcher(bannedWords, exceptions);
    }

    public boolean containsBanned(String value) {
        return matcher.matches(value);
    }
}
