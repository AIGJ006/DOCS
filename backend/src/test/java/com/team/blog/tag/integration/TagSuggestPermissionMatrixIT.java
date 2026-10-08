package com.team.blog.tag.integration;

import com.team.blog.support.permission.AbstractPermissionMatrixIT;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * AI 태그 추천 권한 매트릭스 (013 T020, research R14). {@code post-write.csv}의 owner {@code 013} 행만 돌린다 — 전체
 * 파일은 공용 {@code PermissionMatrixIT}도 돌리므로, 이 클래스는 이 기능만 빠르게 확인할 때 쓴다({@code
 * -Dit.test=TagSuggest*IT}).
 */
class TagSuggestPermissionMatrixIT extends AbstractPermissionMatrixIT {

    static Stream<Arguments> rows() throws IOException {
        try (InputStream in =
                TagSuggestPermissionMatrixIT.class.getResourceAsStream(
                        "/permission/post-write.csv")) {
            String csv = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return csv
                    .lines()
                    .skip(1)
                    .filter(line -> line.endsWith(",013"))
                    .map(line -> line.split(",", -1))
                    .map(c -> Arguments.of(c[0], c[1], c[2], c[3], c[4], c[5]))
                    .toList()
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
    @MethodSource("rows")
    void tagSuggest(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verify(actor, target, action, status, code, owner);
    }
}
