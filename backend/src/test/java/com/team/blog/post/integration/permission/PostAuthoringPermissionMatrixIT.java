package com.team.blog.post.integration.permission;

import com.team.blog.support.permission.AbstractPermissionMatrixIT;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 002 권한 매트릭스 러너 (T042·T070·T090, FR-036, SC-008). {@code post-write.csv}에서 {@code owner=002} 행만
 * 실행한다(새 글·에디터 열기·자동 저장·수동 저장·발행·변경 취소). 다른 기능의 행은 그 기능의 러너(004 T045 {@code PermissionMatrixIT})가
 * 맡는다.
 */
class PostAuthoringPermissionMatrixIT extends AbstractPermissionMatrixIT {

    static Stream<Arguments> rows() throws IOException {
        try (InputStream in =
                        PostAuthoringPermissionMatrixIT.class.getResourceAsStream(
                                "/permission/post-write.csv");
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().skip(1).filter(l -> !l.isBlank()).toList();
            return lines.stream()
                    .map(line -> line.split(",", -1))
                    .filter(cols -> PostAuthoringPermissionActions.OWNER.equals(cols[5].strip()))
                    .map(cols -> Arguments.of((Object[]) cols))
                    .toList()
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
    @MethodSource("rows")
    void post_write_002_행(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verify(actor, target, action, status, code, owner);
    }
}
