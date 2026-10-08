package com.team.blog.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 공용 조건 우회 방지 (004 T043, FR-009, 06 R-2). {@code backend/src/main/java} 아래 소스의 문자열(SQL·JPQL)에서 전체
 * 공개 조건({@code visibility = 'PUBLIC'}, {@code visibility IN (... 'PUBLIC' ...)}, JPQL enum 글자
 * {@code Visibility.PUBLIC})을 찾아, {@code VisibilityFilter.java}·{@code PublicVisibilityRule.java}
 * 밖에 있으면 실패한다 — 목록마다 조건을 따로 만들면 비공개·숨김·탈퇴 유예 글이 새는 곳이 생긴다.
 *
 * <p>Java 코드의 {@code Visibility.PUBLIC} 비교(예: 캐시 정책)는 SQL 조건이 아니라 대상이 아니다. 주석은 건너뛰고 문자열 글자(텍스트 블록
 * 포함)만 본다. 바로 이어 붙인 두 문자열({@code "p.visibility = " + "'PUBLIC'"})도 함께 본다.
 */
class VisibilityFilterUsageGuardTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Set<String> ALLOWED =
            Set.of("VisibilityFilter.java", "PublicVisibilityRule.java");

    private static final List<Pattern> PUBLIC_CONDITIONS =
            List.of(
                    Pattern.compile("visibility\\s*=\\s*'PUBLIC'", Pattern.CASE_INSENSITIVE),
                    Pattern.compile(
                            "visibility\\s+IN\\s*\\([^)]*'PUBLIC'", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("Visibility\\.PUBLIC"));

    @Test
    void 전체_공개_조건_문자열은_공용_조건_두_파일에만_있다() throws IOException {
        List<String> violations = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                scanned++;
                if (ALLOWED.contains(file.getFileName().toString())) {
                    continue;
                }
                for (String literal : sqlCandidates(read(file))) {
                    if (matches(literal)) {
                        violations.add(MAIN.relativize(file) + ": " + literal.strip());
                    }
                }
            }
        }

        assertThat(scanned).as("검사한 소스 수").isGreaterThan(50);
        assertThat(violations).as("목록 조건은 VisibilityFilter.forViewer만 쓴다 (FR-009)").isEmpty();
    }

    @Test
    void 허용된_두_파일에는_실제로_조건이_있다() {
        String rule = read(MAIN.resolve("com/team/blog/post/domain/PublicVisibilityRule.java"));
        assertThat(sqlCandidates(rule)).anyMatch(VisibilityFilterUsageGuardTest::matches);
    }

    @Test
    void 검사기는_주석을_건너뛰고_텍스트_블록과_이어_붙인_문자열을_본다() {
        String source =
                """
                class X {
                    // "p.visibility = 'PUBLIC'" 는 주석
                    /* "visibility='PUBLIC'" 도 주석 */
                    /** {@code p.visibility = 'PUBLIC'} */
                    boolean ok = v == Visibility.PUBLIC;
                    char c = '"';
                    String a = "SELECT 1 WHERE p.visibility = :vis";
                    String b = \"""
                        SELECT * FROM post WHERE visibility = 'PUBLIC'
                        \""";
                    String c2 = "WHERE p.visibility = " + "'PUBLIC'";
                    String d = "SELECT p FROM Post p WHERE p.visibility = com.team.Visibility.PUBLIC";
                    String e = "p.visibility IN ('PUBLIC','FRIENDS')";
                    String f = "escaped \\" quote";
                }
                """;

        List<String> hits =
                sqlCandidates(source).stream()
                        .filter(VisibilityFilterUsageGuardTest::matches)
                        .toList();

        assertThat(hits).hasSize(4);
        assertThat(hits).noneMatch(s -> s.contains(":vis"));
    }

    private static boolean matches(String literal) {
        return PUBLIC_CONDITIONS.stream().anyMatch(p -> p.matcher(literal).find());
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 문자열 글자 각각과, {@code +}로 바로 이어 붙인 두 글자를 합친 것. */
    static List<String> sqlCandidates(String source) {
        List<Literal> literals = literals(source);
        List<String> candidates = new ArrayList<>();
        for (int i = 0; i < literals.size(); i++) {
            Literal current = literals.get(i);
            candidates.add(current.text());
            if (i + 1 < literals.size()) {
                Literal next = literals.get(i + 1);
                String between = source.substring(current.end(), next.start());
                if (between.strip().equals("+")) {
                    candidates.add(current.text() + next.text());
                }
            }
        }
        return candidates;
    }

    private record Literal(String text, int start, int end) {}

    /** 주석·문자 글자를 건너뛰며 문자열 글자(텍스트 블록 포함)를 모은다. */
    private static List<Literal> literals(String s) {
        List<Literal> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                int eol = s.indexOf('\n', i);
                i = eol < 0 ? n : eol + 1;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int close = s.indexOf("*/", i + 2);
                i = close < 0 ? n : close + 2;
            } else if (c == '\'') {
                int j = i + 1;
                while (j < n && s.charAt(j) != '\'') {
                    j += s.charAt(j) == '\\' ? 2 : 1;
                }
                i = j + 1;
            } else if (s.startsWith("\"\"\"", i)) {
                int close = s.indexOf("\"\"\"", i + 3);
                int end = close < 0 ? n : close + 3;
                out.add(new Literal(s.substring(i + 3, close < 0 ? n : close), i, end));
                i = end;
            } else if (c == '"') {
                StringBuilder text = new StringBuilder();
                int j = i + 1;
                while (j < n && s.charAt(j) != '"') {
                    if (s.charAt(j) == '\\' && j + 1 < n) {
                        text.append(s.charAt(j + 1));
                        j += 2;
                    } else {
                        text.append(s.charAt(j));
                        j++;
                    }
                }
                out.add(new Literal(text.toString(), i, j + 1));
                i = j + 1;
            } else {
                i++;
            }
        }
        return out;
    }
}
