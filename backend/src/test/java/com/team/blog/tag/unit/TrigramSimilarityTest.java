package com.team.blog.tag.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.tag.application.suggest.TrigramSimilarity;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** 3-gram Jaccard (013 T011, research R8). */
class TrigramSimilarityTest {

    /** 무작위 한글 음절 글 (같은 씨앗이면 같은 글). 실제 글처럼 3-gram이 대부분 서로 다르다. */
    static String article(int seed, int chars) {
        Random random = new Random(seed);
        StringBuilder sb = new StringBuilder();
        while (sb.length() < chars) {
            int word = 2 + random.nextInt(4);
            for (int i = 0; i < word; i++) {
                sb.append((char) ('가' + random.nextInt(11172)));
            }
            sb.append(' ');
        }
        return sb.substring(0, chars);
    }

    @Test
    void 같으면_1() {
        String a = article(1, 2000);
        assertThat(TrigramSimilarity.jaccard(a, a)).isEqualTo(1.0);
    }

    @Test
    void 오타_3개는_0_9_이상() {
        String a = article(2, 2000);
        StringBuilder b = new StringBuilder(a);
        b.setCharAt(100, 'X');
        b.setCharAt(900, 'Y');
        b.setCharAt(1700, 'Z');
        assertThat(TrigramSimilarity.jaccard(a, b.toString())).isGreaterThanOrEqualTo(0.9);
    }

    @Test
    void 문단_하나_추가는_0_9_미만() {
        String a = article(3, 2000);
        String b = a + " " + article(99, 600);
        assertThat(TrigramSimilarity.jaccard(a, b)).isLessThan(0.9);
    }

    @Test
    void 세_글자_미만이면_0() {
        assertThat(TrigramSimilarity.jaccard("ab", "ab")).isZero();
        assertThat(TrigramSimilarity.jaccard("", "abc")).isZero();
        assertThat(TrigramSimilarity.jaccard(null, "abc")).isZero();
        assertThat(TrigramSimilarity.jaccard("abc", "abc")).isEqualTo(1.0);
    }

    @Test
    void 서로게이트_쌍은_한_글자() {
        // 😀😀 는 코드 포인트 2개라 3-gram이 없다
        assertThat(TrigramSimilarity.jaccard("😀😀", "😀😀")).isZero();
        assertThat(TrigramSimilarity.jaccard("😀😀😀", "😀😀😀")).isEqualTo(1.0);
        assertThat(TrigramSimilarity.jaccard("😀a😀", "😀b😀")).isZero();
    }
}
