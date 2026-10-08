import type { PostCard } from './reading';

/**
 * 팔로우·피드 응답 타입 (010 contracts/openapi.yaml 그대로).
 */

/** `PUT`/`DELETE /api/members/{handle}/follow` 응답 (contracts `FollowState`). */
export interface FollowState {
  /** 지금 내가 팔로우 중인가 */
  following: boolean;
  /** 대상의 최신 팔로워 수 (탈퇴 유예 회원 제외, 다른 사람 변화 포함) */
  followerCount: number;
}

/** 팔로워·팔로잉 목록 항목 (contracts `FollowListItem`). */
export interface FollowListItem {
  handle: string;
  nickname: string;
  /** 작은 프로필 사진. 없으면 null → 기본 아이콘 */
  profileImageUrl: string | null;
  /** 소개 원문. 화면은 첫 줄만 텍스트로 보인다 */
  bio: string | null;
  /** 보는 사람이 이 회원을 팔로우 중인가 (비회원은 false) */
  followedByMe: boolean;
  /** 보는 사람 자신인가 (버튼 없음) */
  isMe: boolean;
}

/** 팔로워·팔로잉 목록 한 페이지 (contracts `FollowListPage`). */
export interface FollowListPage {
  items: FollowListItem[];
  nextCursor: string | null;
}

/** 팔로잉 피드 한 페이지 (contracts `FeedPage`). 카드는 005 `PostCard` 그대로. */
export interface FeedPage {
  items: PostCard[];
  nextCursor: string | null;
  /** 팔로우한 사람이 한 명이라도 있는가. 첫 페이지가 비었을 때만 실제 값이고 그 밖에는 true */
  hasFollowing: boolean;
}
