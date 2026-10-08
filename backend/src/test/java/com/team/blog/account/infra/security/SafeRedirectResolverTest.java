package com.team.blog.account.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/** 로그인 후 이동 주소 검사 (R-33, FR-039). 화면 {@code safeRedirect.test.ts}와 같은 표. */
class SafeRedirectResolverTest {

    private final SafeRedirectResolver resolver = new SafeRedirectResolver();

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "/ | /",
                "/settings | /settings",
                "/settings?tab=1 | /settings?tab=1",
                "/@kim755030/posts/3 | /@kim755030/posts/3",
                "/search?q=a%2Fb | /search?q=a%2Fb",
                "//evil.com | /",
                "///evil.com | /",
                "/\\evil.com | /",
                "https://evil.com | /",
                "http:/evil.com | /",
                "javascript:alert(1) | /",
                "evil.com | /",
                "%2F%2Fevil.com | /",
                "/%2F%2Fevil.com | /",
                "/%5Cevil.com | /",
                "/%252F%252Fevil.com | /",
                "/a\\b | /",
                "/a%0d%0aSet-Cookie:x | /",
                "/%E0%A4%A | /",
            })
    void resolves(String candidate, String expected) {
        assertThat(resolver.resolve(candidate)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void emptyIsRoot(String candidate) {
        assertThat(resolver.resolve(candidate)).isEqualTo("/");
    }

    @org.junit.jupiter.api.Test
    void controlCharacters() {
        assertThat(resolver.resolve("/a\tb")).isEqualTo("/");
        assertThat(resolver.resolve("/a\u0000b")).isEqualTo("/");
        assertThat(resolver.resolve("/" + "a".repeat(3000))).isEqualTo("/");
    }
}
