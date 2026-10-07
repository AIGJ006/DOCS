package com.team.blog.post.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.domain.PostReasonCode;
import com.team.blog.post.domain.TitleNormalizer;
import java.text.Normalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 발행 제목 정리 (002 T035, FR-004, docs/12 §7-4·§9-3 제목 항목). */
class TitleNormalizerTest {

    @Test
    void NFD_한글은_NFC로_바뀐다() {
        String nfd = Normalizer.normalize("한글 제목", Normalizer.Form.NFD);
        assertThat(nfd).isNotEqualTo("한글 제목");
        assertThat(TitleNormalizer.normalize(nfd)).isEqualTo("한글 제목");
    }

    @ParameterizedTest(name = "U+{0}")
    @ValueSource(
            strings = {
                "200B", "200C", "200D", "200E", "200F", "2060", "2061", "2064", "2066", "2069",
                "FEFF", "202A", "202B", "202C", "202D", "202E"
            })
    void 보이지_않는_글자와_방향_제어_문자를_지운다(String hex) {
        String ch = Character.toString(Integer.parseInt(hex, 16));
        assertThat(TitleNormalizer.normalize("a" + ch + "b" + ch)).isEqualTo("ab");
    }

    @Test
    void 방향_뒤집기_문자로_만든_가짜_확장자도_원래_순서로_보인다() {
        // "invoice‮fdp.exe"는 화면에 "invoiceexe.pdf"처럼 보인다 (12 §9-3)
        assertThat(TitleNormalizer.normalize("invoice‮fdp.exe")).isEqualTo("invoicefdp.exe");
    }

    @Test
    void 제어_문자를_지운다() {
        assertThat(TitleNormalizer.normalize("제\u0000목\u0007\u001B\u007F\u0085")).isEqualTo("제목");
        assertThat(TitleNormalizer.normalize("줄\n바꿈\t탭")).isEqualTo("줄바꿈탭");
    }

    @Test
    void 앞뒤_공백을_지운다() {
        assertThat(TitleNormalizer.normalize("  　 JPA N+1 정리   ")).isEqualTo("JPA N+1 정리");
    }

    @Test
    void null은_빈_문자열이다() {
        assertThat(TitleNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void 정리_후_0자면_TITLE_REQUIRED() {
        String normalized = TitleNormalizer.normalize(" ​‮﻿ ");
        assertThat(normalized).isEmpty();
        assertThat(TitleNormalizer.check(normalized, 100)).contains(PostReasonCode.TITLE_REQUIRED);
    }

    @Test
    void 정리_후_101자면_TITLE_TOO_LONG_100자는_통과() {
        assertThat(TitleNormalizer.check("가".repeat(101), 100))
                .contains(PostReasonCode.TITLE_TOO_LONG);
        assertThat(TitleNormalizer.check("가".repeat(100), 100)).isEmpty();
        // 폭 0 문자를 지운 뒤 100자면 통과
        String padded = "가".repeat(100) + "​​";
        assertThat(TitleNormalizer.check(TitleNormalizer.normalize(padded), 100)).isEmpty();
    }

    @Test
    void 글자_수는_코드_포인트로_센다() {
        // 이모지 하나 = 2 char지만 1자 (DB varchar(100)과 같은 기준)
        String emoji = "😀".repeat(100);
        assertThat(emoji.length()).isEqualTo(200);
        assertThat(TitleNormalizer.check(emoji, 100)).isEmpty();
    }
}
