package com.team.blog.post.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 등록된 {@link VisibilityRule} Bean 목록 = 공개 범위 허용값 집합 (research R-21, data-model §3). 같은 값의 규칙이 둘이면
 * 기동에 실패한다.
 *
 * <p>요청 값 검사는 {@link #require(String, String)} 한 곳에서 한다 — 004 공개 범위 변경, 002 발행, 001 기본 공개 범위 설정이 함께
 * 쓴다. {@code FRIENDS}를 적용하지 않은 환경은 규칙 Bean이 없으므로 자동으로 400이다.
 */
@Component
public class VisibilityRegistry {

    private final Map<Visibility, VisibilityRule> rules;

    public VisibilityRegistry(List<VisibilityRule> rules) {
        Map<Visibility, VisibilityRule> map = new EnumMap<>(Visibility.class);
        for (VisibilityRule rule : rules) {
            VisibilityRule previous = map.putIfAbsent(rule.visibility(), rule);
            if (previous != null) {
                throw new IllegalStateException(
                        "같은 공개 범위의 규칙이 둘입니다: "
                                + rule.visibility()
                                + " ("
                                + previous.getClass().getSimpleName()
                                + ", "
                                + rule.getClass().getSimpleName()
                                + ")");
            }
        }
        this.rules = Collections.unmodifiableMap(map);
    }

    /** 허용값 집합 (enum 선언 순서). */
    public Set<Visibility> allowedValues() {
        return rules.keySet();
    }

    /** 등록된 값만 받는다. 보통 {@link #require(String, String)}로 얻은 값을 넘긴다. */
    public VisibilityRule rule(Visibility visibility) {
        VisibilityRule rule = rules.get(visibility);
        if (rule == null) {
            throw new IllegalStateException("등록되지 않은 공개 범위입니다: " + visibility);
        }
        return rule;
    }

    /** 등록된 규칙 (목록 조건 조합용, enum 선언 순서). */
    public Collection<VisibilityRule> rules() {
        return rules.values();
    }

    /**
     * 요청 문자열을 공개 범위로 바꾼다. 대소문자·앞뒤 공백을 고치지 않는다(정확히 같은 이름만).
     *
     * @param field 오류의 칸 이름 (예: {@code visibility}, {@code defaultVisibility})
     * @throws InvalidVisibilityException 허용 집합 밖이면 400 {@code INVALID_VISIBILITY}
     */
    public Visibility require(String raw, String field) {
        if (raw != null) {
            for (Visibility candidate : rules.keySet()) {
                if (candidate.name().equals(raw)) {
                    return candidate;
                }
            }
        }
        throw new InvalidVisibilityException(field);
    }
}
