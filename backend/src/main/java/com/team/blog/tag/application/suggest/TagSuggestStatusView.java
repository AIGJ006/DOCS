package com.team.blog.tag.application.suggest;

/**
 * 추천 버튼 상태 (013 data-model §4 {@code TagSuggestStatus}).
 *
 * @param available 기능 켜짐 ∧ 이 글로 쓸 수 있는 공급자가 있음 ∧ 저장소 정상
 * @param consentRequired 동의 없음 ∨ 버전 다름
 * @param consentVersion 현재 AI 동의 버전
 * @param provider 지금 고를 공급자 예측 ({@code available = false}면 {@code null})
 * @param remainingToday 오늘 남은 횟수
 */
public record TagSuggestStatusView(
        boolean available,
        boolean consentRequired,
        String consentVersion,
        Provider provider,
        int remainingToday) {}
