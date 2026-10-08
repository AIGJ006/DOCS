import type { PostCard } from '../../api/types/reading';
import { LIST_RESTORE_TTL_MINUTES } from '../../config';

/**
 * 목록 뒤로 가기 복원 저장소 (005 T068, FR-018, research R-10).
 *
 * - 이 탭의 `sessionStorage`에 `list-restore:{목록키}`(홈 `home`, 블로그 `blog:{handle}`)로 불러온 카드·다음 위치 값·
 *   스크롤 위치·보관 시각을 둔다. `localStorage`는 쓰지 않는다 — 다른 탭에 오래된 목록이 나타나지 않게.
 * - 보관한 지 `LIST_RESTORE_TTL_MINUTES`(30분)가 지났거나, 값이 없거나, 깨졌으면 `null` → 처음 9개부터.
 * - `sessionStorage` 접근이 막혀도(사생활 보호 모드·저장 공간 초과) 예외를 내지 않는다 — 복원은 편의 기능이다.
 */
export interface ListRestoreState {
  items: PostCard[];
  nextCursor: string | null;
  scrollY: number;
}

export interface SavedListState extends ListRestoreState {
  /** 보관 시각 (epoch 밀리초) */
  savedAt: number;
}

const PREFIX = 'list-restore:';

export function storageKey(listKey: string): string {
  return PREFIX + listKey;
}

export function save(listKey: string, state: ListRestoreState): void {
  const value: SavedListState = {
    items: state.items,
    nextCursor: state.nextCursor,
    scrollY: Math.max(0, Math.round(state.scrollY)),
    savedAt: Date.now(),
  };
  try {
    sessionStorage.setItem(storageKey(listKey), JSON.stringify(value));
  } catch {
    // 보관하지 못하면 다음에 처음부터 보여 준다
  }
}

export function load(listKey: string): SavedListState | null {
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(storageKey(listKey));
  } catch {
    return null;
  }
  if (raw === null) {
    return null;
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return null;
  }
  if (!isSaved(parsed)) {
    return null;
  }
  const age = Date.now() - parsed.savedAt;
  if (age < 0 || age > LIST_RESTORE_TTL_MINUTES * 60_000) {
    return null;
  }
  return parsed;
}

export function clear(listKey: string): void {
  try {
    sessionStorage.removeItem(storageKey(listKey));
  } catch {
    // 무시
  }
}

function isSaved(value: unknown): value is SavedListState {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const v = value as Record<string, unknown>;
  return (
    Array.isArray(v.items) &&
    v.items.every(
      (item) =>
        typeof item === 'object' && item !== null && typeof (item as PostCard).id === 'number',
    ) &&
    (v.nextCursor === null || typeof v.nextCursor === 'string') &&
    typeof v.scrollY === 'number' &&
    Number.isFinite(v.scrollY) &&
    typeof v.savedAt === 'number' &&
    Number.isFinite(v.savedAt)
  );
}
