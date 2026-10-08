/**
 * 팔로우 화면 문구 (010 24 §2, FR-010·FR-012·FR-021, research R9). 끝에 마침표를 붙이지 않는다(README "정해진 것").
 */
export const FOLLOW_MESSAGES = {
  /** 팔로우 안 한 상태의 버튼 */
  follow: '팔로우',
  /** 팔로우 중 (✓는 장식이라 화면 읽기에서는 빠진다) */
  following: '팔로잉',
  /** 팔로우 중인 버튼에 마우스를 올리거나 초점을 줬을 때 */
  unfollow: '언팔로우',
  /** 요청 실패 — 버튼은 누르기 전 상태로 되돌린다 */
  failed: '잠시 후 다시 시도해 주세요',
  /** 피드: 팔로우한 사람이 없을 때 (뒤에 [홈] 링크) */
  feedNoFollowing: '팔로우한 사람이 없어요. 홈에서 읽고 싶은 블로그를 찾아보세요',
  /** 피드: 팔로우한 사람은 있지만 공개 글이 없을 때 */
  feedNoPosts: '팔로우한 사람의 공개 글이 아직 없어요',
  /** 팔로워 목록이 비었을 때 */
  noFollowers: '아직 팔로워가 없어요',
  /** 팔로잉 목록이 비었을 때 */
  noFollowing: '아직 팔로우한 사람이 없어요',
  /** 목록을 불러오지 못했을 때 */
  listFailed: '목록을 불러오지 못했어요',
} as const;

/** 목록 제목: "김민서님의 팔로워" / "김민서님의 팔로잉" */
export function followListTitle(nickname: string, mode: 'followers' | 'following'): string {
  return `${nickname}님의 ${mode === 'followers' ? '팔로워' : '팔로잉'}`;
}

/** 소개의 첫 줄만 (화면은 텍스트 노드로만 보인다). 비었으면 null. */
export function firstLine(bio: string | null): string | null {
  if (bio === null) {
    return null;
  }
  const line = bio.split(/\r?\n/, 1)[0].trim();
  return line === '' ? null : line;
}
