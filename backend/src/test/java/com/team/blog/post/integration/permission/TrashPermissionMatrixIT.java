package com.team.blog.post.integration.permission;

import com.team.blog.post.permission.TrashPermissionOwner;
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
 * 006 권한 매트릭스 러너 (T019·T026·T052, 42 §5-2). {@code post-write.csv}에서 {@code owner=006} 행(삭제·복구·영구
 * 삭제)만 실행한다. 004 공용 러너({@code PermissionMatrixIT}, 004 T045)가 생기면 그쪽이 모든 행을 돌리고 이 러너는 지운다 —
 * 002·005와 같은 방식.
 */
class TrashPermissionMatrixIT extends AbstractPermissionMatrixIT {

    static Stream<Arguments> rows() throws IOException {
        try (InputStream in =
                        TrashPermissionMatrixIT.class.getResourceAsStream(
                                "/permission/post-write.csv");
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().skip(1).filter(l -> !l.isBlank()).toList();
            return lines.stream()
                    .map(line -> line.split(",", -1))
                    .filter(cols -> TrashPermissionOwner.OWNER.equals(cols[5].strip()))
                    .map(cols -> Arguments.of((Object[]) cols))
                    .toList()
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
    @MethodSource("rows")
    void post_write_006_행(
            String actor, String target, String action, String status, String code, String owner)
            throws Exception {
        verify(actor, target, action, status, code, owner);
    }
}
