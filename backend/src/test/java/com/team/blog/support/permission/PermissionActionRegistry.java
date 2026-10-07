package com.team.blog.support.permission;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 등록된 {@link PermissionAction} 실행기 목록 (이름 → 실행기). 같은 이름이 둘이면 만들 수 없다. 실행기가 없는 CSV 행은 {@link
 * AbstractPermissionMatrixIT}가 {@code Assumptions.abort("pending: <owner spec>")}로 건너뛴다.
 */
public final class PermissionActionRegistry {

    private final Map<String, PermissionAction> actions;

    public PermissionActionRegistry(List<PermissionAction> actions) {
        Map<String, PermissionAction> map = new LinkedHashMap<>();
        for (PermissionAction action : actions == null ? List.<PermissionAction>of() : actions) {
            PermissionAction previous = map.putIfAbsent(action.name(), action);
            if (previous != null && previous.getClass() != action.getClass()) {
                throw new IllegalStateException(
                        "같은 이름의 권한 매트릭스 실행기가 둘입니다: "
                                + action.name()
                                + " ("
                                + previous.getClass().getName()
                                + ", "
                                + action.getClass().getName()
                                + ")");
            }
        }
        this.actions = Collections.unmodifiableMap(map);
    }

    public Optional<PermissionAction> find(String name) {
        return Optional.ofNullable(actions.get(name));
    }

    public Map<String, PermissionAction> all() {
        return actions;
    }
}
