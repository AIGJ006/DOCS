import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  createCategory,
  deleteCategory,
  listMyCategories,
  reorderCategories,
  updateCategory,
  type CategoryNode,
  type MyCategories,
} from '../api/categories';
import { ApiError } from '../api/client';
import { useConfirm } from '../components/useConfirm';
import { countCategories, moveSibling } from '../features/category/categoryTree';
import '../features/category/category.css';

export const LOAD_FAILED_TEXT = '카테고리를 불러오지 못했어요';
export const HAS_CHILDREN_TEXT = '하위 카테고리를 먼저 옮기거나 지워 주세요';
const UNKNOWN_ERROR_TEXT = '잠시 후 다시 시도해 주세요';

function messageOf(error: unknown): string {
  if (error instanceof ApiError) {
    return error.errors[0]?.message ?? error.message;
  }
  return UNKNOWN_ERROR_TEXT;
}

/**
 * 카테고리 관리 (017 US1, FR-013·FR-014). 나무 목록, [추가](이름 + 상위), 줄마다 [이름 바꾸기]·상위 선택·[위로]·[아래로]·[삭제].
 * 삭제는 글이 있으면 "글 N개는 분류 없음이 돼요"로 확인하고, 하위가 있으면 지우지 않고 이유를 알린다. 순서가 다른 탭에서 바뀌어
 * 409가 오면 목록을 새로 불러온다. 비회원은 공용 클라이언트가 로그인으로 보낸다.
 */
export default function ManageCategoriesPage() {
  const [data, setData] = useState<MyCategories | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [newName, setNewName] = useState('');
  const [newParent, setNewParent] = useState<number | null>(null);
  const [editing, setEditing] = useState<{ id: number; name: string } | null>(null);
  const { confirm, dialog } = useConfirm();

  const reload = useCallback(async () => {
    try {
      setData(await listMyCategories());
      setLoadError(false);
    } catch {
      setLoadError(true);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    listMyCategories().then(
      (loaded) => {
        if (!cancelled) {
          setData(loaded);
        }
      },
      () => {
        if (!cancelled) {
          setLoadError(true);
        }
      },
    );
    return () => {
      cancelled = true;
    };
  }, []);

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      await reload();
      return true;
    } catch (caught) {
      setError(messageOf(caught));
      if (caught instanceof ApiError && caught.code === 'CATEGORY_ORDER_STALE') {
        await reload();
      }
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function add() {
    const ok = await run(() => createCategory(newName, newParent));
    if (ok) {
      setNewName('');
    }
  }

  async function rename(id: number, name: string) {
    if (await run(() => updateCategory(id, { name }))) {
      setEditing(null);
    }
  }

  async function remove(node: CategoryNode) {
    if (node.children.length > 0) {
      setError(HAS_CHILDREN_TEXT);
      return;
    }
    const message =
      node.postCount > 0
        ? `글 ${node.postCount.toLocaleString('ko-KR')}개는 분류 없음이 돼요`
        : '이 카테고리를 지울까요?';
    if (!(await confirm({ title: `'${node.name}' 삭제`, message, confirmLabel: '삭제' }))) {
      return;
    }
    await run(() => deleteCategory(node.id));
  }

  if (loadError && data === null) {
    return (
      <main className="category-manage" data-route="manage-categories">
        <p role="alert">
          {LOAD_FAILED_TEXT}{' '}
          <button type="button" onClick={() => void reload()}>
            다시 시도
          </button>
        </p>
      </main>
    );
  }
  if (data === null) {
    return <main className="category-manage" aria-busy="true" />;
  }

  const tops = data.items;
  const full = countCategories(tops) >= data.maxCount;

  function row(node: CategoryNode, parent: CategoryNode | null, siblings: CategoryNode[]) {
    const ids = siblings.map((s) => s.id);
    const up = moveSibling(ids, node.id, -1);
    const down = moveSibling(ids, node.id, 1);
    const isEditing = editing?.id === node.id;
    const parentChoices = tops.filter((t) => t.id !== node.id);
    return (
      <div className="category-row">
        {isEditing ? (
          <>
            <input
              type="text"
              aria-label="새 이름"
              value={editing.name}
              maxLength={60}
              onChange={(event) => setEditing({ id: node.id, name: event.target.value })}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  void rename(node.id, editing.name);
                }
              }}
            />
            <button
              type="button"
              disabled={busy}
              onClick={() => void rename(node.id, editing.name)}
            >
              저장
            </button>
            <button type="button" onClick={() => setEditing(null)}>
              취소
            </button>
          </>
        ) : (
          <>
            <span className="category-row__name">{node.name}</span>
            <span className="category-row__count">글 {node.postCount.toLocaleString('ko-KR')}</span>
          </>
        )}
        <div className="category-row__actions">
          {!isEditing ? (
            <button
              type="button"
              disabled={busy}
              onClick={() => setEditing({ id: node.id, name: node.name })}
            >
              이름 바꾸기
            </button>
          ) : null}
          {node.children.length === 0 ? (
            <select
              aria-label={`${node.name} 상위 카테고리`}
              value={parent === null ? '' : String(parent.id)}
              disabled={busy}
              onChange={(event) =>
                void run(() =>
                  updateCategory(node.id, {
                    parentId: event.target.value === '' ? null : Number(event.target.value),
                  }),
                )
              }
            >
              <option value="">최상위</option>
              {parentChoices.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} 아래
                </option>
              ))}
            </select>
          ) : null}
          <button
            type="button"
            aria-label={`${node.name} 위로`}
            disabled={busy || up === null}
            onClick={() => up && void run(() => reorderCategories(parent?.id ?? null, up))}
          >
            ↑
          </button>
          <button
            type="button"
            aria-label={`${node.name} 아래로`}
            disabled={busy || down === null}
            onClick={() => down && void run(() => reorderCategories(parent?.id ?? null, down))}
          >
            ↓
          </button>
          <button type="button" disabled={busy} onClick={() => void remove(node)}>
            삭제
          </button>
        </div>
      </div>
    );
  }

  return (
    <main className="category-manage" data-route="manage-categories">
      <div className="category-manage__header">
        <h1>카테고리 관리</h1>
        <Link to="/manage/posts">내 글 관리</Link>
      </div>

      <form
        className="category-add"
        onSubmit={(event) => {
          event.preventDefault();
          void add();
        }}
      >
        <input
          type="text"
          aria-label="새 카테고리 이름"
          placeholder="새 카테고리 이름"
          value={newName}
          maxLength={60}
          onChange={(event) => setNewName(event.target.value)}
          disabled={full}
        />
        <select
          aria-label="상위 카테고리"
          value={newParent === null ? '' : String(newParent)}
          onChange={(event) =>
            setNewParent(event.target.value === '' ? null : Number(event.target.value))
          }
          disabled={full}
        >
          <option value="">최상위</option>
          {tops.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name} 아래
            </option>
          ))}
        </select>
        <button type="submit" disabled={busy || full}>
          카테고리 추가
        </button>
      </form>
      {full ? (
        <p className="category-row__count">
          카테고리는 {data.maxCount.toLocaleString('ko-KR')}개까지 만들 수 있어요
        </p>
      ) : null}

      {error ? (
        <p role="alert" className="category-error">
          {error}
        </p>
      ) : null}

      {tops.length === 0 ? (
        <p>아직 카테고리가 없어요. 위에서 첫 카테고리를 만들어 보세요.</p>
      ) : (
        <ul className="category-list" aria-label="카테고리 목록">
          {tops.map((top) => (
            <li key={top.id}>
              {row(top, null, tops)}
              {top.children.length > 0 ? (
                <ul>
                  {top.children.map((child) => (
                    <li key={child.id}>{row(child, top, top.children)}</li>
                  ))}
                </ul>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      {dialog}
    </main>
  );
}
