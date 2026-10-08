/**
 * 좋아요 API (009 contracts/openapi.yaml `likePost`·`unlikePost`). 요청은 001 `client.ts`를 쓴다(CSRF 헤더·오류 본문).
 *
 * 상태 지정 방식이다 — `PUT`은 "좋아요 상태로", `DELETE`는 "취소 상태로". 이미 그 상태여도 200이고 응답의 `likeCount`는 다른 사람의
 * 변화까지 반영한 지금 값이다. 404여도 공통 404 화면으로 바꾸지 않는다 — 좋아요 실패 안내만 한다(`notFoundScreen: false`).
 */
import { apiDelete, apiPut } from './client';

export interface LikeState {
  liked: boolean;
  likeCount: number;
}

export function putLike(postId: number | string): Promise<LikeState> {
  return apiPut<LikeState>(`/api/posts/${postId}/like`, undefined, { notFoundScreen: false });
}

export function deleteLike(postId: number | string): Promise<LikeState> {
  return apiDelete<LikeState>(`/api/posts/${postId}/like`, undefined, { notFoundScreen: false });
}
