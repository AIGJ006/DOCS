/**
 * 이 기기(IndexedDB) 임시 글 보관 (002 T083, FR-014, data-model §5). localforage를 쓴다.
 *
 * - `draft:{memberId}:{postId}` = { title, contentMd, baseVersion, dirty, pendingImages, updatedAt }
 * - `draft-backup:{memberId}:{postId}` = { title, contentMd, baseVersion, backedUpAt } — [저장된 내용 불러오기], 7일 보관
 *
 * 로그인 정보·토큰은 넣지 않는다. 로그아웃 때 001이 `clearMemberDrafts(memberId)`를 부른다(main.tsx에서 등록).
 */
import localforage from 'localforage';
import { EDITOR_CONFIG } from './editorConfig';

/** 업로드가 끝나지 않은 사진 (003 규칙: 본문의 `local:{localId}` 주소 ↔ 원본 Blob). */
export interface PendingImage {
  localId: string;
  blob: Blob;
}

export interface LocalDraft {
  title: string;
  contentMd: string;
  /** 이 내용이 출발한 서버 편집 버전. */
  baseVersion: number;
  /** 서버에 아직 보내지 않은 변경이 있는가. */
  dirty: boolean;
  pendingImages: PendingImage[];
  /** 이 기기에 저장한 시각(ms). */
  updatedAt: number;
}

export interface DraftBackup {
  title: string;
  contentMd: string;
  baseVersion: number;
  backedUpAt: number;
}

let store: LocalForage | null = null;

/** localforage 저장소 (테스트에서 직접 읽을 때도 쓴다). */
export function draftStorage(): LocalForage {
  if (!store) {
    store = localforage.createInstance({ name: 'devlog', storeName: 'editor_drafts' });
  }
  return store;
}

export function draftKey(memberId: number, postId: number): string {
  return `draft:${memberId}:${postId}`;
}

export function backupKey(memberId: number, postId: number): string {
  return `draft-backup:${memberId}:${postId}`;
}

export async function loadDraft(memberId: number, postId: number): Promise<LocalDraft | null> {
  return (await draftStorage().getItem<LocalDraft>(draftKey(memberId, postId))) ?? null;
}

export async function saveDraft(
  memberId: number,
  postId: number,
  draft: LocalDraft,
): Promise<void> {
  const value: LocalDraft = {
    title: draft.title,
    contentMd: draft.contentMd,
    baseVersion: draft.baseVersion,
    dirty: draft.dirty,
    pendingImages: draft.pendingImages ?? [],
    updatedAt: draft.updatedAt,
  };
  await draftStorage().setItem(draftKey(memberId, postId), value);
}

export async function removeDraft(memberId: number, postId: number): Promise<void> {
  await draftStorage().removeItem(draftKey(memberId, postId));
}

export async function loadBackup(memberId: number, postId: number): Promise<DraftBackup | null> {
  return (await draftStorage().getItem<DraftBackup>(backupKey(memberId, postId))) ?? null;
}

export async function saveBackup(
  memberId: number,
  postId: number,
  backup: DraftBackup,
): Promise<void> {
  const value: DraftBackup = {
    title: backup.title,
    contentMd: backup.contentMd,
    baseVersion: backup.baseVersion,
    backedUpAt: backup.backedUpAt,
  };
  await draftStorage().setItem(backupKey(memberId, postId), value);
}

/** 그 회원의 dirty 임시 글 목록 (로그아웃 전 전송용). */
export async function listDirtyDrafts(
  memberId: number,
): Promise<{ postId: number; draft: LocalDraft }[]> {
  const prefix = `draft:${memberId}:`;
  const result: { postId: number; draft: LocalDraft }[] = [];
  for (const key of await draftStorage().keys()) {
    if (!key.startsWith(prefix)) {
      continue;
    }
    const postId = Number(key.slice(prefix.length));
    const draft = await draftStorage().getItem<LocalDraft>(key);
    if (draft?.dirty && Number.isFinite(postId)) {
      result.push({ postId, draft });
    }
  }
  return result;
}

/** 보관 기간(7일)이 지난 백업을 지운다. */
export async function purgeExpiredBackups(now: number = Date.now()): Promise<void> {
  const storage = draftStorage();
  for (const key of await storage.keys()) {
    if (!key.startsWith('draft-backup:')) {
      continue;
    }
    const backup = await storage.getItem<DraftBackup>(key);
    if (!backup || now - backup.backedUpAt > EDITOR_CONFIG.backupRetentionMs) {
      await storage.removeItem(key);
    }
  }
}

/** 그 회원의 `draft:`·`draft-backup:` 키를 모두 지운다 (001 로그아웃이 부른다, US3 #7). */
export async function clearMemberDrafts(memberId: number): Promise<void> {
  const storage = draftStorage();
  const prefixes = [`draft:${memberId}:`, `draft-backup:${memberId}:`];
  for (const key of await storage.keys()) {
    if (prefixes.some((prefix) => key.startsWith(prefix))) {
      await storage.removeItem(key);
    }
  }
}

/** 테스트 전용: 저장소를 비운다. */
export async function resetLocalDraftsForTests(): Promise<void> {
  await draftStorage().clear();
}
