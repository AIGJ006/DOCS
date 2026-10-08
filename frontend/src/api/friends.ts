import { apiDelete, apiGet, apiPut } from './client';

/** 나와 그 회원의 관계 (contracts `FriendshipView.status`). */
export type FriendshipStatus = 'SELF' | 'NONE' | 'REQUEST_SENT' | 'REQUEST_RECEIVED' | 'FRIENDS';

/** 최근 활동 구간 (contracts `LastActive`, US8). */
export interface LastActive {
  bucket: string;
  days?: number;
  [key: string]: unknown;
}

export interface FriendshipView {
  status: FriendshipStatus;
  /** 표시 조건을 만족할 때만 있다 (US8). */
  lastActive?: LastActive;
}

export interface FriendItem {
  handle: string;
  nickname: string;
  profileImageUrl: string | null;
  friendsSince: string;
  lastActive?: LastActive;
}

export interface FriendRequestItem {
  handle: string;
  nickname: string;
  profileImageUrl: string | null;
  requestedAt: string;
}

export interface CursorList<T> {
  items: T[];
  nextCursor: string | null;
}

function friendPath(handle: string): string {
  return `/api/members/${encodeURIComponent(handle)}/friend`;
}

function withCursor(path: string, cursor?: string | null): string {
  return cursor ? `${path}?cursor=${encodeURIComponent(cursor)}` : path;
}

export function getFriendship(handle: string): Promise<FriendshipView> {
  return apiGet<FriendshipView>(friendPath(handle), { notFoundScreen: false });
}

/** 요청 / 받은 요청 수락 (변화 없으면 그대로). */
export function requestFriendship(handle: string): Promise<FriendshipView> {
  return apiPut<FriendshipView>(friendPath(handle), undefined, { notFoundScreen: false });
}

/** 거절 / 요청 취소 / 친구 끊기 — 상대에게 알리지 않는다. */
export function removeFriendship(handle: string): Promise<FriendshipView> {
  return apiDelete<FriendshipView>(friendPath(handle), undefined, { notFoundScreen: false });
}

export function listMyFriends(cursor?: string | null): Promise<CursorList<FriendItem>> {
  return apiGet<CursorList<FriendItem>>(withCursor('/api/me/friends', cursor));
}

export function listFriendRequests(cursor?: string | null): Promise<CursorList<FriendRequestItem>> {
  return apiGet<CursorList<FriendRequestItem>>(withCursor('/api/me/friend-requests', cursor));
}
