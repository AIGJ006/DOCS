package com.team.blog.tag.application;

import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.Viewer;
import com.team.blog.tag.domain.TagNormalizer;
import com.team.blog.tag.infra.TagQueryRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 태그 조회 (008 T032·T050·T058). 다른 모듈(discovery 태그 목록·페이지 셸·블로그 필터, 012 검색)은 정규화도 이 Service로 부른다.
 *
 * <p>API는 <b>정규화된 이름만</b> 받는다 — 정규화 결과와 다르거나 형식이 틀리면 404(리다이렉트 없음, 005 R-23). 301은 화면 주소(페이지 셸)만
 * 한다.
 */
@Service
@Transactional(readOnly = true)
public class TagQueryService {

    private final TagNormalizer normalizer;
    private final TagQueryRepository repository;
    private final TagProperties properties;

    public TagQueryService(
            TagNormalizer normalizer, TagQueryRepository repository, TagProperties properties) {
        this.normalizer = normalizer;
        this.repository = repository;
        this.properties = properties;
    }

    /** 전체 태그 목록 — 공개 글 수 많은 순 상위 {@code blog.tag.top-limit}개. */
    public TagIndexView top() {
        return new TagIndexView(
                repository.top(properties.topLimit()).stream()
                        .map(row -> new TagCountView(row.name(), row.postCount()))
                        .toList());
    }

    /** 검색어·주소 값 정규화 (금칙어 검사 없음). 형식이 틀리면 빈 값 — {@link TagNormalizer#normalizeQuery}. */
    public Optional<String> normalizeQuery(String raw) {
        return normalizer.normalizeQuery(raw);
    }

    /**
     * API 경로·쿼리의 태그 이름 확인.
     *
     * @throws NotFoundException 정규화 결과와 다르거나 형식이 틀림
     */
    public void requireCanonical(String name) {
        if (name == null || !normalizer.normalizeQuery(name).map(name::equals).orElse(false)) {
            throw new NotFoundException("not a canonical tag name");
        }
    }

    /** 머리말 "#name · 공개 글 N". 태그가 없어도 형식에 맞으면 {@code {name, 0}}. */
    public TagSummaryView summary(String name) {
        requireCanonical(name);
        return new TagSummaryView(name, repository.countPublic(name));
    }

    /** 블로그 태그 줄 — 블로그 목록과 같은 조건, 최대 {@code blog.tag.blog-strip.limit}개와 처음 보일 개수. */
    public BlogTagsView blogTags(Viewer viewer, long ownerId) {
        TagProperties.BlogStrip strip = properties.blogStrip();
        return new BlogTagsView(
                repository.blogTags(viewer, ownerId, strip.limit()).stream()
                        .map(row -> new TagCountView(row.name(), row.postCount()))
                        .toList(),
                strip.initial());
    }

    /** 태그 번호 (discovery 카드 목록의 태그 조건). 없으면 빈 값. */
    public Optional<Long> findIdByName(String name) {
        return repository.findIdByName(name);
    }
}
