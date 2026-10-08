package com.team.blog.tag.application.suggest;

import com.team.blog.tag.domain.TagNormalization;
import com.team.blog.tag.domain.TagNormalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 결과 검사 (013 T026, contracts/providers.md §7, research R9). 008 {@link TagNormalizer#normalize}(금칙어
 * 포함 9단계)로 정리하고 받아들인 것만 남긴 뒤, 이미 붙인 태그를 빼고 남은 자리만큼 자른다.
 */
@Component
public class SuggestResultFilter {

    private final TagNormalizer normalizer;
    private final TagSuggestProperties properties;
    private final SuggestPostLimits limits;

    public SuggestResultFilter(
            TagNormalizer normalizer, TagSuggestProperties properties, SuggestPostLimits limits) {
        this.normalizer = normalizer;
        this.properties = properties;
        this.limits = limits;
    }

    /** AI가 준 문자열 → 받아들인 정규화 이름 (중복은 처음 것만). 재사용 저장소에는 이 목록을 둔다. */
    public List<String> accepted(List<String> raw) {
        Set<String> out = new LinkedHashSet<>();
        for (String tag : raw) {
            if (normalizer.normalize(tag) instanceof TagNormalization.Accepted a) {
                out.add(a.name());
            }
        }
        return List.copyOf(out);
    }

    /** 이번 요청에 더 줄 수 있는 개수 = {@code min(max-suggestions, max-tags − 붙인 수)}, 0 이상. */
    public int slots(List<String> currentTags) {
        return Math.max(
                0, Math.min(properties.maxSuggestions(), limits.maxTags() - currentTags.size()));
    }

    /** 붙인 태그를 같은 정규화로 정리한 이름 (형식이 틀린 것은 뺀다). */
    public List<String> normalizedCurrent(List<String> currentTags) {
        return accepted(currentTags);
    }

    /** 받아들인 이름에서 붙인 태그를 빼고 남은 자리만큼. */
    public List<String> select(List<String> accepted, List<String> currentTags) {
        Set<String> current = Set.copyOf(normalizedCurrent(currentTags));
        return accepted.stream()
                .filter(t -> !current.contains(t))
                .limit(slots(currentTags))
                .toList();
    }
}
