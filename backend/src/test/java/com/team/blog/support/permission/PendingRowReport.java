package com.team.blog.support.permission;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 권한 매트릭스 대기 행 보고 (004 T048, SC-001). 실행기가 없어 {@code pending: <owner>}로 건너뛴 행을 owner별로 모으고, 매트릭스 러너
 * 클래스가 끝나면 테스트 로그에 집계를 남긴다. 002·006·014가 실행기를 등록할 때마다 남은 칸이 줄어드는지 보고, Polish T075에서 Tier A 행이 0건인지
 * {@link #pendingOwners()}로 확인한다.
 *
 * <p>{@link AbstractPermissionMatrixIT}가 {@code @ExtendWith}로 붙인다(하위 클래스에 상속).
 */
public final class PendingRowReport implements AfterAllCallback {

    private static final Logger log = LoggerFactory.getLogger(PendingRowReport.class);

    /** 러너 클래스 이름 → (owner → 건너뛴 행). */
    private static final Map<String, Map<String, List<String>>> PENDING = new ConcurrentHashMap<>();

    /** 건너뛴 행을 적는다. */
    static void record(Class<?> runner, String owner, String row) {
        PENDING.computeIfAbsent(runner.getName(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(owner, k -> new CopyOnWriteArrayList<>())
                .add(row);
    }

    /** 지금까지 모든 러너에서 건너뛴 행 (owner → 행 목록, owner 순). */
    public static Map<String, List<String>> pendingOwners() {
        Map<String, List<String>> merged = new TreeMap<>();
        PENDING.values()
                .forEach(
                        byOwner ->
                                byOwner.forEach(
                                        (owner, rows) ->
                                                merged.computeIfAbsent(
                                                                owner,
                                                                k -> new CopyOnWriteArrayList<>())
                                                        .addAll(rows)));
        return merged;
    }

    /** 한 러너에서 건너뛴 행 (owner → 행 목록). */
    public static Map<String, List<String>> pendingOwners(Class<?> runner) {
        return new TreeMap<>(PENDING.getOrDefault(runner.getName(), Map.of()));
    }

    /** 집계 문장 (예: {@code "pending rows: 014=14"}, 없으면 {@code "pending rows: 0"}). */
    public static String summary(Map<String, List<String>> byOwner) {
        if (byOwner.isEmpty()) {
            return "pending rows: 0";
        }
        StringBuilder sb = new StringBuilder("pending rows:");
        byOwner.forEach(
                (owner, rows) -> sb.append(' ').append(owner).append('=').append(rows.size()));
        return sb.toString();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        Class<?> runner = context.getRequiredTestClass();
        Map<String, List<String>> byOwner = pendingOwners(runner);
        log.info("[권한 매트릭스] {} {}", runner.getSimpleName(), summary(byOwner));
        byOwner.forEach(
                (owner, rows) -> rows.forEach(row -> log.info("  pending {}: {}", owner, row)));
    }
}
