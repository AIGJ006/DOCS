package com.team.blog.account.application.policy;

import java.util.Set;

/**
 * 주소 중복 조회 (⑩ 단계). {@code base}와 같거나 {@code base + "_"}로 시작하는 기존 주소를 한 번에 돌려준다 — 운영 구현은 {@code
 * handle = ? OR handle LIKE '{base}\_%'} 한 번({@code uq_member_handle} 인덱스, {@code
 * account.infra.JdbcHandleLookup}).
 */
@FunctionalInterface
public interface HandleLookup {

    Set<String> takenWithBase(String base);
}
