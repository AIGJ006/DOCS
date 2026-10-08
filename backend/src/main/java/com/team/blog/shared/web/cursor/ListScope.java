package com.team.blog.shared.web.cursor;

import java.util.Objects;

/**
 * 커서 안의 목록 구분 값({@code l}). 커서는 만든 목록에서만 쓸 수 있다 — 다른 목록의 커서는 400 {@code INVALID_CURSOR} (005 R-24,
 * 006 R16).
 *
 * <p>값 규칙: {@code home}, {@code blog:{handle}}, {@code me:friends}, {@code me:friend-requests},
 * {@code manage:{tab}[:{filter}]}(006), {@code tag:{name}}·{@code blog:{handle}:tag:{name}}(008 —
 * 정규화된 태그 이름에는 공백·{@code :}이 없다) 등. 기능은 {@link #of(String)}로 자기 값을 만든다.
 */
public record ListScope(String value) {

    public ListScope {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("목록 구분 값이 비었거나 공백을 포함합니다: '" + value + "'");
        }
    }

    public static ListScope of(String value) {
        return new ListScope(value);
    }

    /** 전체 글 목록 (005). */
    public static ListScope home() {
        return new ListScope("home");
    }

    /** 개인 블로그 글 목록 (005). handle은 소문자. */
    public static ListScope blog(String handle) {
        return new ListScope("blog:" + handle);
    }

    /** 태그별 글 목록 (008). name은 정규화된 태그 이름. */
    public static ListScope tag(String name) {
        return new ListScope("tag:" + name);
    }

    /** 블로그 안 태그 필터 목록 (008). handle은 소문자, name은 정규화된 태그 이름. */
    public static ListScope blogTag(String handle, String name) {
        return new ListScope("blog:" + handle + ":tag:" + name);
    }

    /** 내 친구 목록 (001 US7). */
    public static ListScope myFriends() {
        return new ListScope("me:friends");
    }

    /** 받은 친구 요청 목록 (001 US7). */
    public static ListScope friendRequests() {
        return new ListScope("me:friend-requests");
    }

    @Override
    public String toString() {
        return value;
    }
}
