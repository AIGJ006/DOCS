package com.team.blog.discovery.application;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 개인 블로그 머리말 (005 T047, contracts {@code BlogHeader}, data-model §5). 010 팔로우가 팔로워 수 등을 더할 수 있게 둔다.
 *
 * <p>{@code isMe}는 JSON 이름을 그대로 쓴다 — record 접근자 {@code isMe()}가 {@code me}로 줄어들지 않게 {@link
 * JsonProperty}로 못 박았다.
 *
 * @param handle 블로그 주소 (소문자)
 * @param nickname 닉네임
 * @param bio 소개 (줄바꿈 그대로, 없으면 {@code null})
 * @param profileImageUrl 작은 프로필 사진 주소 (썸네일, 없으면 원본; 사진이 없으면 {@code null})
 * @param publicPostCount 이 블로그에 보이는 글 수 (주인이 봐도 같다)
 * @param isMe 내 블로그인가
 */
public record BlogHeaderView(
        String handle,
        String nickname,
        String bio,
        String profileImageUrl,
        long publicPostCount,
        @JsonProperty("isMe") boolean isMe) {}
