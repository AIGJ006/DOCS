/**
 * 팔로우·피드 API (010 contracts/openapi.yaml). 요청은 001 `client.ts`를 쓴다(CSRF 헤더·오류 본문).
 *
 * 팔로우는 상태 지정 방식이다 — `PUT`은 "팔로우 상태로", `DELETE`는 "해제 상태로". 이미 그 상태여도 200이고 응답의
 * `followerCount`는 다른 사람의 변화까지 반영한 지금 값이다. 팔로우 요청의 404는 공통 404 화면으로 바꾸지 않는다 — 버튼 실패 안내만
 * 한다(`notFoundScreen: false`). 목록 크기 `size`는 보내지 않는다 — 서버가 설정값으로 고정한다.
 */
import { apiDelete, apiGet, apiPut } from './client';
import type { FeedPage, FollowListPage, FollowState } from './types/follow';

function followPath(handle: string): string {
  return `/api/members/${encodeURIComponent(handle)}/follow`;
}

function withCursor(path: string, cursor?: string | null): string {
  return cursor ? `${path}?cursor=${encodeURIComponent(cursor)}` : path;
}

export function follow(handle: string): Promise<FollowState> {
  return apiPut<FollowState>(followPath(handle), undefined, { notFoundScreen: false });
}

export function unfollow(handle: string): Promise<FollowState> {
  return apiDelete<FollowState>(followPath(handle), undefined, { notFoundScreen: false });
}

/** 팔로워 목록 (누구나). 없는 주소·탈퇴 유예는 404 → 공통 404 화면. */
export function listFollowers(handle: string, cursor?: string | null): Promise<FollowListPage> {
  return apiGet<FollowListPage>(
    withCursor(`/api/members/${encodeURIComponent(handle)}/followers`, cursor),
  );
}

/** 팔로잉 목록 (누구나). */
export function listFollowing(handle: string, cursor?: string | null): Promise<FollowListPage> {
  return apiGet<FollowListPage>(
    withCursor(`/api/members/${encodeURIComponent(handle)}/following`, cursor),
  );
}

/** 팔로잉 피드 (로그인 필요 — 비회원은 401). */
export function getFeed(cursor?: string | null): Promise<FeedPage> {
  return apiGet<FeedPage>(withCursor('/api/feed', cursor));
}
