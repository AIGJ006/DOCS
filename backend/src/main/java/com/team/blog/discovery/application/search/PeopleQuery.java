package com.team.blog.discovery.application.search;

/**
 * 사람 검색어 한 덩어리 (012 data-model §3, research R10, Clarifications Q3). 공백을 모두 지우고 맨 앞 {@code @} 하나를
 * 지운 값이며 2 코드 포인트 이상이다.
 *
 * @param token 닉네임·블로그 주소와 부분 일치로 비교할 값
 */
public record PeopleQuery(String token) {

    @Override
    public String toString() {
        return "PeopleQuery[length=" + token.codePointCount(0, token.length()) + "]";
    }
}
