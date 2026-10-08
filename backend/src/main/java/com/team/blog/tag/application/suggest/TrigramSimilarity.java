package com.team.blog.tag.application.suggest;

import java.util.HashSet;
import java.util.Set;

/**
 * 3-gram Jaccard 유사도 (013 T012, research R8, contracts/providers.md §6). 3-gram은 코드 포인트 단위(서로게이트 쌍은
 * 한 글자), 공백 포함. 둘 중 하나라도 3자 미만이면 0.
 */
public final class TrigramSimilarity {

    private TrigramSimilarity() {}

    public static double jaccard(String a, String b) {
        Set<String> x = trigrams(a);
        Set<String> y = trigrams(b);
        if (x.isEmpty() || y.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        Set<String> small = x.size() <= y.size() ? x : y;
        Set<String> large = small == x ? y : x;
        for (String gram : small) {
            if (large.contains(gram)) {
                intersection++;
            }
        }
        int union = x.size() + y.size() - intersection;
        return (double) intersection / union;
    }

    static Set<String> trigrams(String s) {
        Set<String> grams = new HashSet<>();
        if (s == null) {
            return grams;
        }
        int[] cps = s.codePoints().toArray();
        for (int i = 0; i + 3 <= cps.length; i++) {
            grams.add(new String(cps, i, 3));
        }
        return grams;
    }
}
