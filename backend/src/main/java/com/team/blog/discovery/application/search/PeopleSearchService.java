package com.team.blog.discovery.application.search;

import com.team.blog.discovery.application.SearchQueryTooShortException;
import com.team.blog.discovery.infra.PeopleSearchRepository;
import com.team.blog.media.application.ImageUrlResolver;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 사람 검색 (012 T034, US3 #1·#2, research R10, FR-036). 판정 순서: 검색어(400) → 요청 제한(429, 맨 끝) → SQL 1번. 운영
 * 로그에는 길이·결과 수·걸린 시간만(FR-039).
 */
@Service
public class PeopleSearchService {

    private static final Logger log = LoggerFactory.getLogger(PeopleSearchService.class);

    private final SearchQueryParser parser;
    private final PeopleSearchRepository repository;
    private final ImageUrlResolver imageUrls;
    private final SearchProperties properties;

    public PeopleSearchService(
            SearchQueryParser parser,
            PeopleSearchRepository repository,
            ImageUrlResolver imageUrls,
            SearchProperties properties) {
        this.parser = parser;
        this.repository = repository;
        this.imageUrls = imageUrls;
        this.properties = properties;
    }

    /**
     * @param beforeSearch 판정을 통과한 뒤 SQL 전에 부른다 (요청 제한)
     * @throws SearchQueryTooShortException 덩어리가 2글자 미만
     */
    public PeopleSearchResult search(String q, Runnable beforeSearch) {
        long started = System.nanoTime();
        PeopleQuery query = parser.parsePeople(q).orElseThrow(SearchQueryTooShortException::new);
        beforeSearch.run();
        List<PersonItem> items =
                repository.search(query.token(), properties.peopleLimit()).stream()
                        .map(
                                row ->
                                        new PersonItem(
                                                row.handle(),
                                                row.nickname(),
                                                imageUrls.publicUrl(row.profileKey()),
                                                PersonItem.firstLine(row.bio())))
                        .toList();
        log.info(
                "search people len={} results={} took={}ms",
                query.token().codePointCount(0, query.token().length()),
                items.size(),
                (System.nanoTime() - started) / 1_000_000);
        return new PeopleSearchResult(items);
    }
}
