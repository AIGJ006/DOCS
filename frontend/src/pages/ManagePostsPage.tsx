import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import {
  discardEditing,
  type ManagePostItem,
  type ManageTab,
  type VisibilityFilter,
} from '../api/managePosts';
import { createPost, setVisibility, type SetVisibilityResult, type Visibility } from '../api/posts';
import { useConfirm } from '../components/useConfirm';
import { useToast } from '../components/useToast';
import { useSession } from '../features/auth/useSession';
import DraftRow from '../features/manage-posts/DraftRow';
import ManageTabs from '../features/manage-posts/ManageTabs';
import PublishedRow from '../features/manage-posts/PublishedRow';
import TrashRow from '../features/manage-posts/TrashRow';
import {
  DISCARD_CONFIRM,
  MAKE_PUBLIC_CONFIRM,
  TRASH_NOTICE,
} from '../features/manage-posts/confirmDialogs';
import { useManagePosts } from '../features/manage-posts/useManagePosts';
import { useRowAction } from '../features/manage-posts/useRowAction';
import { useTrashActions } from '../features/manage-posts/useTrashActions';
import '../features/manage-posts/managePosts.css';

const TABS: readonly ManageTab[] = ['drafts', 'published', 'trash'];
const FILTERS: { value: VisibilityFilter | null; label: string }[] = [
  { value: null, label: '전체' },
  { value: 'public', label: '공개' },
  { value: 'private', label: '비공개' },
];
const EMPTY: Record<ManageTab, string> = {
  drafts: '임시글이 없어요',
  published: '발행한 글이 없어요',
  trash: '휴지통이 비어 있어요',
};
const NEW_POST_FAILED = '새 글을 만들지 못했어요';

function parseTab(value: string | null): ManageTab {
  return TABS.includes(value as ManageTab) ? (value as ManageTab) : 'drafts';
}

function parseFilter(value: string | null): VisibilityFilter | null {
  return value === 'public' || value === 'private' ? value : null;
}

/**
 * 내 글 관리 `/manage/posts?tab=drafts|published|trash&visibility=public|private` (006 T051·T070·T071, US3·US5).
 *
 * - 본인 글만 보인다 — 주소에 사용자를 가리키는 값이 없다(FR-002). 비회원은 로그인 화면으로 간다(FR-001).
 * - 처리 결과는 그 줄에만 반영하고 실패하면 그 줄 아래에 이유를 보인다(FR-013). 다시 불러온 목록에 그 줄이 없으면
 *   이유를 목록 위에 보인다.
 * - 검색·일괄 처리는 없다(FR-004, 41 M-10). 375px에서 버튼은 줄을 바꿔 가로 스크롤이 생기지 않는다.
 */
export default function ManagePostsPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const { me } = useSession();
  const tab = parseTab(params.get('tab'));
  const visibility = tab === 'published' ? parseFilter(params.get('visibility')) : null;

  const list = useManagePosts({ tab, visibility });
  const { confirm, dialog } = useConfirm();
  const { show, toast } = useToast();
  const location = useLocation();
  const draftSaved = (location.state as { draftSaved?: unknown } | null)?.draftSaved === true;
  useEffect(() => {
    // 에디터 [임시저장]으로 나온 경우
    if (draftSaved) {
      show({ text: '임시저장했어요' });
    }
  }, [draftSaved, location.key, show]);
  const rowAction = useRowAction({ reload: list.reload });
  const actions = useTrashActions({
    confirm,
    rowAction,
    showToast: show,
    onRowRemoved: list.removeRow,
    onCountsChange: list.adjustCounts,
  });
  const [pageError, setPageError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const now = new Date();

  const orphanErrors = useMemo(() => {
    const shown = new Set(list.items.map((row) => row.id));
    return [
      ...new Set(
        Object.entries(rowAction.rowErrors)
          .filter(([id]) => !shown.has(Number(id)))
          .map(([, message]) => message),
      ),
    ];
  }, [list.items, rowAction.rowErrors]);

  function select(nextTab: ManageTab, nextFilter: VisibilityFilter | null = null) {
    const next = new URLSearchParams({ tab: nextTab });
    if (nextTab === 'published' && nextFilter) {
      next.set('visibility', nextFilter);
    }
    setParams(next);
  }

  async function discard(row: ManagePostItem) {
    if (!(await confirm(DISCARD_CONFIRM))) {
      return;
    }
    await rowAction.run(row.id, () => discardEditing(row.id), {
      onSuccess: () => list.updateRow(row.id, { editing: false }),
    });
  }

  /** [공개 범위 ▾] (T068, FR-015): 성공하면 그 줄의 값만 바꾼다(다시 발행·"수정됨" 없음). 실패는 그 줄 아래에 */
  async function changeVisibility(
    row: ManagePostItem,
    next: Visibility,
  ): Promise<SetVisibilityResult | null> {
    let saved: SetVisibilityResult | null = null;
    await rowAction.run(row.id, () => setVisibility(row.id, next, { notFoundScreen: false }), {
      onSuccess: (result) => {
        saved = result;
        list.updateRow(row.id, { visibility: result.visibility });
      },
    });
    return saved;
  }

  /** 공개로 바꿀 때만 확인한다 (FR-015 "모든 사람이 볼 수 있게 돼요") */
  function confirmVisibility(from: Visibility, to: Visibility) {
    return to === 'PUBLIC' && from !== 'PUBLIC' ? confirm(MAKE_PUBLIC_CONFIRM) : true;
  }

  async function newPost() {
    setCreating(true);
    setPageError(null);
    try {
      const created = await createPost();
      navigate(`/write/${created.postId}`);
    } catch (caught) {
      setCreating(false);
      setPageError(caught instanceof ApiError ? caught.message : NEW_POST_FAILED);
    }
  }

  function renderRow(row: ManagePostItem) {
    const common = {
      row,
      busy: rowAction.isBusy(row.id),
      error: rowAction.rowErrors[row.id],
    };
    if (tab === 'trash') {
      return (
        <TrashRow
          key={row.id}
          {...common}
          now={now}
          onRestore={(r) => void actions.restore(r)}
          onPurge={(r) => void actions.purge(r)}
        />
      );
    }
    if (tab === 'published') {
      return (
        <PublishedRow
          key={row.id}
          {...common}
          handle={me?.handle ?? null}
          onTrash={(r) => void actions.trash(r)}
          onDiscard={(r) => void discard(r)}
          confirmVisibility={confirmVisibility}
          onVisibilityChange={changeVisibility}
        />
      );
    }
    return <DraftRow key={row.id} {...common} now={now} onTrash={(r) => void actions.trash(r)} />;
  }

  return (
    <main className="manage-page" data-route="manage-posts">
      <div className="manage-header">
        <h1>내 글 관리</h1>
        <button type="button" onClick={() => void newPost()} disabled={creating}>
          새 글
        </button>
      </div>
      {pageError ? (
        <p className="manage-page-error" role="alert">
          {pageError}
        </p>
      ) : null}

      <ManageTabs current={tab} counts={list.counts} onSelect={(next) => select(next)} />

      <section id="manage-panel" role="tabpanel" aria-labelledby={`manage-tab-${tab}`}>
        {tab === 'published' ? (
          <div className="manage-filters" role="group" aria-label="공개 범위">
            {FILTERS.map(({ value, label }) => (
              <button
                key={label}
                type="button"
                aria-pressed={visibility === value}
                onClick={() => select('published', value)}
              >
                {label}
              </button>
            ))}
          </div>
        ) : null}
        {tab === 'trash' ? <p className="manage-notice">{TRASH_NOTICE}</p> : null}

        {orphanErrors.map((message) => (
          <p key={message} className="manage-row-error" role="alert">
            {message}
          </p>
        ))}
        {list.error ? (
          <p className="manage-page-error" role="alert">
            {list.error}
          </p>
        ) : null}

        {list.items.length > 0 ? (
          <ul className="manage-list">{list.items.map(renderRow)}</ul>
        ) : !list.loading && !list.error ? (
          <p className="manage-empty">{EMPTY[tab]}</p>
        ) : null}

        {list.loading ? <p aria-busy="true">불러오는 중…</p> : null}
        {list.nextCursor && !list.loading ? (
          <button type="button" className="manage-more" onClick={() => void list.loadMore()}>
            더 보기
          </button>
        ) : null}
      </section>
      {dialog}
      {toast}
    </main>
  );
}
