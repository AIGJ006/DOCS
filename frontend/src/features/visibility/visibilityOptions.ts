/**
 * 공개 범위 선택지 (004 T038, FR-001·FR-046). 값·라벨·아이콘은 이 파일 한 곳에서 관리한다 — 발행 설정(002), 글 상세(005),
 * 내 글 관리(006), 설정 화면의 기본 공개 범위(001)가 함께 쓴다.
 *
 * 공통 선택지는 `PUBLIC`·`PRIVATE`뿐이다. `FRIENDS`(친구 공개)는 선택 구현이라 빌드 설정
 * `VITE_FRIENDS_VISIBILITY=true`일 때만 선택지에 나온다(공통 기본 비활성, 2026-10-07 M1). 배지는 값이 오면 셋 다 보인다.
 */
import type { Visibility } from '../../api/posts';

/** 배지·선택지에 쓰는 값 (공통 값 + 선택 구현 `FRIENDS`) — `api/posts.ts`의 `Visibility`와 같다. */
export type VisibilityValue = Visibility;

export interface VisibilityOption {
  value: VisibilityValue;
  /** 화면 글자 (FR-046) */
  label: string;
  /** 그림 글자 (화면 읽기 프로그램은 읽지 않음) */
  icon: string;
  /** 그림 글자만 보일 때 화면 읽기 프로그램이 읽을 짧은 글자 (006 FR-008 "공개"/"비공개") */
  shortLabel: string;
}

export const VISIBILITY_OPTIONS: Record<VisibilityValue, VisibilityOption> = {
  PUBLIC: { value: 'PUBLIC', label: '전체 공개', icon: '🌐', shortLabel: '공개' },
  FRIENDS: { value: 'FRIENDS', label: '친구 공개', icon: '👥', shortLabel: '친구 공개' },
  PRIVATE: { value: 'PRIVATE', label: '나만 보기', icon: '🔒', shortLabel: '비공개' },
};

/** 친구 공개 선택 구현을 켠 빌드인가 (`VITE_FRIENDS_VISIBILITY=true`). */
export const FRIENDS_VISIBILITY_ENABLED: boolean =
  import.meta.env.VITE_FRIENDS_VISIBILITY === 'true';

/** 고를 수 있는 값 (선택지 순서: 전체 공개 → 친구 공개 → 나만 보기). */
export function selectableVisibilities(
  friendsEnabled = FRIENDS_VISIBILITY_ENABLED,
): VisibilityValue[] {
  return friendsEnabled ? ['PUBLIC', 'FRIENDS', 'PRIVATE'] : ['PUBLIC', 'PRIVATE'];
}

/** 선택지 글자 "🌐 전체 공개" */
export function optionText(value: VisibilityValue): string {
  const option = VISIBILITY_OPTIONS[value];
  return `${option.icon} ${option.label}`;
}

/** 400 `INVALID_VISIBILITY`일 때 보일 문구 (서버 문구와 같다) */
export const INVALID_VISIBILITY_MESSAGE = '공개 범위를 다시 선택해 주세요';
