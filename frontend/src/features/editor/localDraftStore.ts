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

/**
 * 업로드가 끝나지 않은 사진 (003 US3 규칙: 본문의 `local:{localId}` 주소 ↔ 원래 파일). 원래 파일은 `ArrayBuffer`와 형식으로
 * 보관한다 — 어느 IndexedDB 구현에서도 그대로 되살아난다. 파일 이름은 넣지 않는다.
 */
export interface PendingImage {
  localId: string;
  type: string;
  data: ArrayBuffer;
  /** 보관한 시각(ms) */
  heldAt: number;
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

/** 같은 임시 글 키에 대한 쓰기를 차례로 돌린다 (자동 저장과 사진 보관이 서로 덮어쓰지 않게). */
const writeChains = new Map<string, Promise<unknown>>();

function serialized<T>(key: string, write: () => Promise<T>): Promise<T> {
  const previous = writeChains.get(key) ?? Promise.resolve();
  const next = previous.catch(() => undefined).then(write);
  writeChains.set(key, next);
  void next
    .finally(() => {
      if (writeChains.get(key) === next) {
        writeChains.delete(key);
      }
    })
    .catch(() => undefined);
  return next;
}

/**
 * 임시 글을 저장한다. `pendingImages`를 주지 않으면(자동 저장) 이미 보관된 대기 사진을 그대로 둔다(003 US3).
 */
export async function saveDraft(
  memberId: number,
  postId: number,
  draft: Omit<LocalDraft, 'pendingImages'> & { pendingImages?: PendingImage[] },
): Promise<void> {
  const key = draftKey(memberId, postId);
  await serialized(key, async () => {
    const pendingImages =
      draft.pendingImages ?? (await draftStorage().getItem<LocalDraft>(key))?.pendingImages ?? [];
    const value: LocalDraft = {
      title: draft.title,
      contentMd: draft.contentMd,
      baseVersion: draft.baseVersion,
      dirty: draft.dirty,
      pendingImages,
      updatedAt: draft.updatedAt,
    };
    await draftStorage().setItem(key, value);
  });
}

/** 대기 사진 목록을 고친다 (003 US3). 임시 글이 아직 없으면 빈 임시 글(dirty 아님)에 붙인다. */
export async function updatePendingImages(
  memberId: number,
  postId: number,
  change: (images: PendingImage[]) => PendingImage[],
): Promise<PendingImage[]> {
  const key = draftKey(memberId, postId);
  return serialized(key, async () => {
    const current = await draftStorage().getItem<LocalDraft>(key);
    const next = change(current?.pendingImages ?? []);
    const value: LocalDraft = current
      ? { ...current, pendingImages: next }
      : {
          title: '',
          contentMd: '',
          baseVersion: 0,
          dirty: false,
          pendingImages: next,
          updatedAt: Date.now(),
        };
    await draftStorage().setItem(key, value);
    return next;
  });
}

/** 대기 사진 목록. */
export async function loadPendingImages(memberId: number, postId: number): Promise<PendingImage[]> {
  return (await loadDraft(memberId, postId))?.pendingImages ?? [];
}

export async function removeDraft(memberId: number, postId: number): Promise<void> {
  const key = draftKey(memberId, postId);
  await serialized(key, () => draftStorage().removeItem(key));
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
