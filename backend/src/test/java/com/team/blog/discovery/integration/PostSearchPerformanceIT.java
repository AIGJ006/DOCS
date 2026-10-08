package com.team.blog.discovery.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.application.search.PostSearchPage;
import com.team.blog.discovery.application.search.PostSearchService;
import com.team.blog.discovery.support.SearchFixtures;
import com.team.blog.discovery.support.SearchFixtures.BulkPost;
import com.team.blog.support.IntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 글 검색 성능 (012 T011, SC-001, 33 §8). 기본 빌드에서는 돌지 않는다 — {@code ./mvnw verify
 * -Dit.test=PostSearchPerformanceIT -Dblog.perf=true}(008 {@code TagPerformanceIT}와 같은 방식, pom을 바꾸지
 * 않으려고 {@code @Tag("slow")} + 시스템 속성 조건).
 *
 * <p>시드: 회원 300명, 글 1만 개 → 측정(헌법 목록 기준 p95 300ms) → 9만 개 더(합 10만) → 측정(p95 500ms). 본문은 낱말 약 6,300개
 * (자주 쓰는 말·기술 낱말·지어낸 낱말)를 Zipf 분포로 고르고 글마다 주제 기술 낱말 3개를 섞은 문장(같은 문장 반복 금지 — 33 §8), 조사를 붙이기도 한다. 길이
 * 200~3,000자, 제목 2~8낱말(주제 낱말 비중이 높다), 글 3개 중 1개에 태그 1개. 검색어 20종(2·3·5글자, 여러 단어, 드문 낱말, 없는 낱말)을
 * 관련도순·최신순으로 재고 후보 SQL의 {@code EXPLAIN (ANALYZE)}를 로그로 남긴다.
 */
@Tag("slow")
@EnabledIfSystemProperty(named = "blog.perf", matches = "true")
class PostSearchPerformanceIT extends IntegrationTestBase {

    private static final String[] WORDS = {
        "스프링",
        "트랜잭션",
        "격리",
        "수준",
        "전파",
        "롤백",
        "커밋",
        "데이터베이스",
        "인덱스",
        "쿼리",
        "조인",
        "서브쿼리",
        "정규화",
        "캐시",
        "레디스",
        "세션",
        "쿠키",
        "보안",
        "인증",
        "인가",
        "토큰",
        "암호화",
        "해시",
        "로그",
        "모니터링",
        "배포",
        "도커",
        "컨테이너",
        "쿠버네티스",
        "파드",
        "서비스",
        "네트워크",
        "프록시",
        "로드밸런서",
        "스케일",
        "성능",
        "튜닝",
        "메모리",
        "스레드",
        "동시성",
        "락",
        "데드락",
        "비동기",
        "이벤트",
        "메시지",
        "큐",
        "카프카",
        "스트림",
        "배치",
        "스케줄러",
        "테스트",
        "단위",
        "통합",
        "목",
        "픽스처",
        "리팩터링",
        "설계",
        "패턴",
        "싱글턴",
        "팩토리",
        "전략",
        "옵저버",
        "어댑터",
        "데코레이터",
        "도메인",
        "엔티티",
        "값",
        "객체",
        "애그리거트",
        "리포지토리",
        "계층",
        "아키텍처",
        "모듈",
        "의존성",
        "주입",
        "컨트롤러",
        "뷰",
        "모델",
        "템플릿",
        "리액트",
        "컴포넌트",
        "상태",
        "훅",
        "렌더링",
        "브라우저",
        "자바스크립트",
        "타입스크립트",
        "자바",
        "코틀린",
        "파이썬",
        "고랭",
        "러스트",
        "컴파일러",
        "가비지",
        "컬렉션",
        "힙",
        "스택",
        "알고리즘",
        "자료구조",
        "정렬",
        "탐색",
        "그래프",
        "트리",
        "해시맵",
        "배열",
        "연결리스트",
        "재귀",
        "동적계획법",
        "탐욕",
        "분할정복",
        "복잡도",
        "오늘",
        "어제",
        "회고",
        "공부",
        "정리",
        "기록",
        "실수",
        "해결",
        "방법",
        "이유",
        "문제",
        "질문",
        "답변",
        "경험",
        "프로젝트",
        "팀",
        "회의",
        "일정",
        "마감",
        "리뷰",
        "코드",
        "버그",
        "장애",
        "원인",
        "분석",
        "결과",
        "개선",
        "측정",
        "지표",
        "목표",
        "계획",
        "그리고",
        "하지만",
        "그래서",
        "따라서",
        "먼저",
        "다음",
        "마지막",
        "정말",
        "아주",
        "조금",
        "많이",
        "항상",
        "가끔",
        "결국",
        "역시",
        "spring",
        "boot",
        "transaction",
        "isolation",
        "propagation",
        "rollback",
        "commit",
        "database",
        "index",
        "query",
        "join",
        "cache",
        "redis",
        "session",
        "cookie",
        "security",
        "token",
        "hash",
        "logging",
        "docker",
        "kubernetes",
        "proxy",
        "performance",
        "memory",
        "thread",
        "async",
        "event",
        "kafka",
        "stream",
        "batch",
        "test",
        "mock",
        "refactoring",
        "design",
        "pattern",
        "domain",
        "entity",
        "repository",
        "controller",
        "react",
        "component",
        "state",
        "hook",
        "browser",
        "javascript",
        "typescript",
        "java",
        "kotlin",
        "python",
        "golang",
        "rust",
        "compiler",
        "garbage",
        "heap",
        "stack",
        "algorithm",
        "sort",
        "graph",
        "tree",
        "array",
        "recursion",
        "lombok",
        "jpa",
        "hibernate",
        "mybatis",
        "gradle",
        "maven",
        "git",
        "github",
        "ci",
        "cd",
        "nginx",
        "linux",
        "shell",
        "vim",
        "api",
        "rest",
        "graphql",
        "grpc",
        "json",
        "yaml",
        "xml",
        "http",
        "tcp",
        "udp",
        "dns",
        "tls",
        "oauth",
        "jwt",
        "postgres",
        "mysql",
        "mongodb",
        "elasticsearch",
        "trigram",
        "gin",
        "btree",
        "explain",
        "analyze",
        "vacuum",
        "wal",
        "replication",
        "sharding",
        "partition",
        "격리수준",
        "읽기전용",
        "낙관적",
        "비관적",
        "멱등성",
        "재시도",
        "타임아웃",
        "서킷브레이커",
        "백프레셔",
        "직렬화",
        "역직렬화",
        "마이그레이션",
        "플라이웨이"
    };

    private static final String[] QUERIES = {
        "트랜잭션",
        "스프링",
        "롬복",
        "jpa",
        "격리수준",
        "트랜잭션 격리",
        "스프링 트랜잭션 전파",
        "데이터베이스",
        "인덱스 튜닝",
        "kafka stream",
        "서킷브레이커",
        "멱등성 재시도",
        "postgres trigram",
        "ab",
        "explain analyze",
        "동적계획법",
        "없는낱말이에요",
        "react hook 상태",
        "가비지 컬렉션",
        "레디스 캐시 세션"
    };

    @Autowired PostSearchService search;

    @Test
    void SC001_글_1만개_300ms_10만개_500ms() {
        List<Long> authors = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            authors.add(members().member().create());
        }
        Random random = new Random(20261008L);
        Instant base = Instant.now().minus(Duration.ofDays(400));
        SearchFixtures fx = new SearchFixtures(jdbc);

        seed(fx, authors, random, base, 0, 10_000);
        long p95Small = measure("1만");
        seed(fx, authors, random, base, 10_000, 100_000);
        long p95Large = measure("10만");
        explain();

        assertThat(p95Small).as("글 1만 개 p95").isLessThan(300);
        assertThat(p95Large).as("글 10만 개 p95").isLessThan(500);
    }

    private void seed(
            SearchFixtures fx, List<Long> authors, Random random, Instant base, int from, int to) {
        for (int start = from; start < to; start += 5_000) {
            List<BulkPost> rows = new ArrayList<>();
            for (int i = start; i < Math.min(to, start + 5_000); i++) {
                String[] topic = {
                    WORDS[random.nextInt(WORDS.length)],
                    WORDS[random.nextInt(WORDS.length)],
                    WORDS[random.nextInt(WORDS.length)]
                };
                rows.add(
                        new BulkPost(
                                authors.get(random.nextInt(authors.size())),
                                text(random, topic, 0.6, 2 + random.nextInt(7), 95),
                                text(random, topic, 0.08, 1_000, 200 + random.nextInt(2_800)),
                                base.plusSeconds(i * 300L + random.nextInt(300))));
            }
            fx.bulk(rows);
        }
        jdbc.update(
                "INSERT INTO tag (name) SELECT lower(w) FROM unnest(?::text[]) AS w"
                        + " WHERE lower(w) ~ '^[가-힣a-z0-9]{1,30}$' ON CONFLICT (name) DO NOTHING",
                (Object) WORDS);
        jdbc.update(
                "INSERT INTO post_tag (post_id, tag_id, position)"
                        + " SELECT p.id, t.id, 0 FROM post p"
                        + " JOIN tag t ON t.id = 1 + (p.id * 7919 % (SELECT count(*) FROM tag))"
                        + " WHERE p.id % 3 = 0 AND NOT EXISTS (SELECT 1 FROM post_tag x WHERE x.post_id = p.id)");
        jdbc.execute("ANALYZE post");
        jdbc.execute("ANALYZE post_tag");
        jdbc.execute("ANALYZE tag");
    }

    /**
     * 낱말을 섞은 글. {@code maxWords}개 또는 {@code maxChars}자에서 멈춘다. 낱말은 {@code topicShare} 비율로 글의 주제 낱말
     * 3개에서, 나머지는 {@link #VOCABULARY}에서 Zipf 분포(s=1)로 고른다 — 자주 쓰는 말은 거의 모든 글에, 기술 낱말은 일부 글에만 나온다.
     */
    private static String text(
            Random random, String[] topic, double topicShare, int maxWords, int maxChars) {
        StringBuilder sb = new StringBuilder();
        int words = 0;
        while (words < maxWords) {
            String w =
                    random.nextDouble() < topicShare
                            ? topic[random.nextInt(topic.length)]
                            : VOCABULARY.get(zipf(random));
            if (random.nextInt(4) == 0) {
                w = w + PARTICLES[random.nextInt(PARTICLES.length)];
            }
            if (sb.length() + w.length() + 1 > maxChars) {
                break;
            }
            if (!sb.isEmpty()) {
                sb.append(random.nextInt(12) == 0 ? ".\n" : " ");
            }
            sb.append(w);
            words++;
        }
        return sb.isEmpty() ? "글" + random.nextInt(1000) : sb.toString();
    }

    private static final String[] PARTICLES = {"을", "를", "은", "는", "이", "가", "에서", "으로", "의", "도"};

    private static final String[] FUNCTION_WORDS = {
        "그리고", "하지만", "그래서", "따라서", "먼저", "다음", "정말", "아주", "조금", "많이", "항상", "가끔", "결국", "역시",
        "오늘", "이번", "그냥", "바로", "다시", "같은", "다른", "모든", "어떤", "이런", "그런", "있다", "없다", "한다", "했다",
        "된다", "보면", "때문에", "경우", "방법", "문제", "코드", "정리", "사용", "설정", "확인"
    };

    /** 자주 쓰는 말 → 기술 낱말 + 지어낸 낱말 6,000개(시드 고정 순서). 순위가 낮을수록 드물다. */
    private static final List<String> VOCABULARY = vocabulary();

    private static final double[] ZIPF_CDF = zipfCdf(VOCABULARY.size());

    private static List<String> vocabulary() {
        Random random = new Random(7L);
        List<String> rest = new ArrayList<>(Arrays.asList(WORDS));
        String syllables = "가나다라마바사아자차카타파하고노도로모보소오조초코토포호구누두루무부수우주추쿠투푸후기니디리미비시이지치키티피히개내대래매배새애재채캐태패해";
        for (int i = 0; i < 6_000; i++) {
            StringBuilder w = new StringBuilder();
            int n = 2 + random.nextInt(3);
            for (int k = 0; k < n; k++) {
                w.append(syllables.charAt(random.nextInt(syllables.length())));
            }
            rest.add(w.toString());
        }
        Collections.shuffle(rest, random);
        List<String> all = new ArrayList<>(Arrays.asList(FUNCTION_WORDS));
        all.addAll(rest);
        return all;
    }

    private static double[] zipfCdf(int n) {
        double[] cdf = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += 1.0 / (i + 1);
            cdf[i] = sum;
        }
        for (int i = 0; i < n; i++) {
            cdf[i] /= sum;
        }
        return cdf;
    }

    private static int zipf(Random random) {
        int found = Arrays.binarySearch(ZIPF_CDF, random.nextDouble());
        return Math.min(ZIPF_CDF.length - 1, found >= 0 ? found : -found - 1);
    }

    private long measure(String label) {
        for (String q : QUERIES) {
            run(q, null); // 데우기
        }
        List<Long> millis = new ArrayList<>();
        for (int round = 0; round < 2; round++) {
            for (String q : QUERIES) {
                for (String sort : new String[] {"relevance", "latest"}) {
                    long started = System.nanoTime();
                    PostSearchPage page = run(q, sort);
                    long took = (System.nanoTime() - started) / 1_000_000;
                    millis.add(took);
                    if (page.nextCursor() != null) {
                        long next = System.nanoTime();
                        search.search(q, sort, page.nextCursor(), null, () -> {});
                        millis.add((System.nanoTime() - next) / 1_000_000);
                    }
                    if (round == 0) {
                        System.out.printf(
                                "search perf %s q=%s sort=%s items=%d took=%dms%n",
                                label, q, sort, page.items().size(), took);
                    }
                }
            }
        }
        long[] sorted = millis.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(sorted);
        long p95 = sorted[(int) Math.ceil(sorted.length * 0.95) - 1];
        System.out.printf(
                "search perf %s n=%d p50=%dms p95=%dms max=%dms%n",
                label, sorted.length, sorted[sorted.length / 2], p95, sorted[sorted.length - 1]);
        return p95;
    }

    private PostSearchPage run(String q, String sort) {
        return search.search(q, sort, null, null, () -> {});
    }

    /** PostSearchRepository 후보 SQL의 CTE와 같은 모양으로 실행 계획을 남긴다. */
    private void explain() {
        String plan =
                String.join(
                        "\n",
                        jdbc.queryForList(
                                "EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM post WHERE title ILIKE"
                                        + " '%트랜잭션%' ESCAPE '\\'"
                                        + " UNION SELECT pt.post_id FROM post_tag pt JOIN tag t ON t.id"
                                        + " = pt.tag_id WHERE t.name LIKE '%트랜잭션%' ESCAPE '\\'"
                                        + " UNION SELECT id FROM post WHERE content_md ILIKE"
                                        + " '%트랜잭션%' ESCAPE '\\'",
                                String.class));
        System.out.println("search perf EXPLAIN\n" + plan);
        assertThat(plan).contains("ix_post_title_trgm").contains("ix_post_content_trgm");
    }
}
