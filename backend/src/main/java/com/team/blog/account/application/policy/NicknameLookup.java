package com.team.blog.account.application.policy;

/**
 * 닉네임 중복 조회 (대소문자 무시, 자기 자신 제외). 운영 구현은 {@code lower(nickname) = lower(?) AND id <> ?} ({@code
 * uq_member_nickname} 인덱스, {@code account.infra.JdbcNicknameLookup}).
 */
@FunctionalInterface
public interface NicknameLookup {

    /**
     * @param excludeMemberId 제외할 회원(프로필 수정 때 자기 자신). 가입이면 null
     */
    boolean existsIgnoreCase(String nickname, Long excludeMemberId);
}
