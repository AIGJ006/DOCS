package com.team.blog.discovery.application.search;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.discovery.application.PostCardAssembler;
import com.team.blog.discovery.application.PostCardView;
import com.team.blog.discovery.application.ReadingProperties;
import com.team.blog.discovery.application.SearchQueryTooShortException;
import com.team.blog.discovery.infra.PostCardQueryRepository;
import com.team.blog.discovery.infra.PostCardRow;
import com.team.blog.discovery.infra.PostSearchRepository;
import com.team.blog.discovery.infra.PostSearchRepository.After;
import com.team.blog.discovery.infra.PostSearchRepository.Hit;
import com.team.blog.discovery.infra.PostSearchRepository.SnippetSource;
import com.team.blog.discovery.infra.PostSearchRepository.StageResult;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.web.cursor.ListScope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 글 검색 (012 T015·T035, US1·US3 #3, research R8·R11, FR-017~035).
 *
 * <p>판정 순서 (openapi 공통 규칙): ① 블로그 안 검색이면 블로그 주인(없음·유예·익명 처리 → 404, 001 {@link
 * MemberQueryService#findReadableBlogOwner}) → ② 정렬·검색어(400) → ③ 커서(400) → ④ 요청 제한(429, 맨 끝 —
 * {@link SearchRateLimit}) → ⑤ 검색. 001 게이트(탈퇴 유예 403)는 이보다 앞이다.
 *
 * <p>한 페이지는 커서의 단계부터 단계를 이어 9 + 1개를 모은다(FR-028). 앞 9개의 번호로 카드 SQL 1번({@link
 * PostCardQueryRepository#findCardsByIds}) + 주변 문장 재료 SQL 1번. 10번째가 있으면 9번째 위치가 {@code
 * nextCursor}다.
 *
 * <p>트랜잭션을 열지 않는다 — 요청 제한(Redis 쓰기)이 SQL 사이에 있고, 읽기는 문장마다 자동 커밋으로 충분하다(그 사이 볼 수 없게 된 글은 카드 SQL이
 * 뺀다). 운영 로그에는 검색어 길이·단어 수·마지막 단계·걸린 시간만 남긴다(FR-039).
 */
@Service
public class PostSearchService {

    private static final Logger log = LoggerFactory.getLogger(PostSearchService.class);

    private static final List<SearchStage> RELEVANCE_STAGES =
            List.of(SearchStage.TITLE, SearchStage.TITLE_OR_TAG, SearchStage.ANYWHERE);

    private final MemberQueryService members;
    private final SearchQueryParser parser;
    private final SearchCursor cursors;
    private final PostSearchRepository repository;
    private final PostCardQueryRepository cards;
    private final PostCardAssembler assembler;
    private final SnippetBuilder snippets;
    private final SearchProperties properties;
    private final ReadingProperties reading;

    public PostSearchService(
            MemberQueryService members,
            SearchQueryParser parser,
            SearchCursor cursors,
            PostSearchRepository repository,
            PostCardQueryRepository cards,
            PostCardAssembler assembler,
            SnippetBuilder snippets,
            SearchProperties properties,
            ReadingProperties reading) {
        this.members = members;
        this.parser = parser;
        this.cursors = cursors;
        this.repository = repository;
        this.cards = cards;
        this.assembler = assembler;
        this.snippets = snippets;
        this.properties = properties;
        this.reading = reading;
    }

    /**
     * @param q 검색어 원문
     * @param sort {@code relevance}(기본)·{@code latest}
     * @param cursor 앞 페이지의 {@code nextCursor}
     * @param blog 블로그 안 검색이면 블로그 주소(대소문자 무시), 아니면 {@code null}
     * @param beforeSearch 판정을 모두 통과한 뒤, SQL 전에 부른다 (요청 제한 — 판정 순서의 맨 끝)
     * @throws NotFoundException 블로그 안 검색의 블로그가 없음·탈퇴 유예·익명 처리
     * @throws SearchQueryTooShortException 남는 단어가 없음
     */
    public PostSearchPage search(
            String q, String sort, String cursor, String blog, Runnable beforeSearch) {
        long started = System.nanoTime();
        Long ownerId = null;
        if (blog != null && !blog.isEmpty()) {
            BlogOwner owner =
                    members.findReadableBlogOwner(MemberQueryService.normalizeHandle(blog))
                            .orElseThrow(() -> new NotFoundException("search blog not found"));
            ownerId = owner.id();
        }
        SearchSort searchSort = SearchSort.parse(sort);
        SearchQuery query = parser.parse(q);
        if (query.isEmpty()) {
            throw new SearchQueryTooShortException();
        }
        ListScope scope = SearchCursor.scope(searchSort, ownerId, query.fingerprint());
        SearchCursor.Key key = cursors.decode(cursor, scope, searchSort);
        beforeSearch.run();

        int pageSize = reading.list().pageSize();
        int need = pageSize + 1;
        List<Hit> hits = new ArrayList<>();
        SearchStage lastStage = null;
        for (SearchStage stage : stagesFrom(searchSort, query, key)) {
            After after =
                    key != null && key.stage() == stage
                            ? new After(key.firstPublicAt(), key.id())
                            : null;
            StageResult result =
                    repository.find(
                            query,
                            stage,
                            ownerId,
                            after,
                            need - hits.size(),
                            properties.recentWindow());
            hits.addAll(result.hits());
            lastStage = stage;
            if (hits.size() >= need) {
                break;
            }
        }
        boolean more = hits.size() > pageSize;
        List<Hit> page = more ? hits.subList(0, pageSize) : hits;
        String nextCursor = null;
        if (more) {
            Hit last = page.get(page.size() - 1);
            nextCursor =
                    cursors.encode(
                            scope,
                            new SearchCursor.Key(last.stage(), last.firstPublicAt(), last.id()));
        }
        List<PostSearchItem> items = items(page, query);
        log.info(
                "search posts len={} words={} stage={} results={} took={}ms",
                query.codePointLength(),
                query.words().size(),
                lastStage,
                items.size(),
                (System.nanoTime() - started) / 1_000_000);
        return new PostSearchPage(
                items,
                nextCursor,
                query.hasTwoCharWord() ? PostSearchPage.TWO_CHAR_TITLE_TAG_ONLY : null);
    }

    private static List<SearchStage> stagesFrom(
            SearchSort sort, SearchQuery query, SearchCursor.Key key) {
        if (sort == SearchSort.LATEST) {
            return List.of(SearchStage.ANY);
        }
        List<SearchStage> stages = new ArrayList<>();
        for (SearchStage stage : RELEVANCE_STAGES) {
            if (key != null && stage.code() < key.stage().code()) {
                continue;
            }
            // 3글자 이상 단어가 없으면 ③은 ②와 조건이 같아 비어 있다
            if (stage == SearchStage.ANYWHERE && !query.anyInContent()) {
                continue;
            }
            stages.add(stage);
        }
        return stages;
    }

    private List<PostSearchItem> items(List<Hit> page, SearchQuery query) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<Long> ids = page.stream().map(Hit::id).toList();
        Map<Long, PostCardRow> rows = new HashMap<>();
        for (PostCardRow row : cards.findCardsByIds(ids)) {
            rows.put(row.id(), row);
        }
        Map<Long, SnippetSource> sources = new HashMap<>();
        for (SnippetSource source : repository.snippetSources(ids)) {
            sources.put(source.id(), source);
        }
        List<PostSearchItem> items = new ArrayList<>();
        for (Hit hit : page) {
            PostCardRow row = rows.get(hit.id());
            if (row == null) {
                continue; // 그 사이 볼 수 없게 된 글
            }
            PostCardView card = assembler.toView(row);
            SnippetSource source = sources.get(hit.id());
            Snippet snippet =
                    source == null
                            ? Snippet.plain(card.excerpt())
                            : snippets.build(
                                    source.title(),
                                    source.contentMd(),
                                    card.excerpt(),
                                    query.words());
            items.add(PostSearchItem.of(card, snippet));
        }
        return items;
    }
}
