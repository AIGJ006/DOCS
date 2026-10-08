/**
 * 2단계 카테고리 (017, 나민서 개인 확장 — 01 §2-4, 02 §7). 카테고리 관리·글의 카테고리 지정·블로그 카테고리 목록·글 상세 경로.
 *
 * <p>{@code category} 테이블은 이 모듈만 쓴다. {@code post.category_id} 쓰기는 post 모듈 {@code
 * PostCategoryAssignment}에 맡긴다(원칙 II). 카테고리별 공개 글 수 SQL만 {@code post}·{@code member}를 읽는다(017 plan
 * Complexity Tracking).
 */
package com.team.blog.category;
