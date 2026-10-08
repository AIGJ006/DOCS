import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { PostDetail } from '../../api/types/reading';

/** 작성자 버튼 자리에 넘기는 값 (004·006 부품이 쓴다). */
export interface AuthorActionContext {
  postId: number;
  visibility: PostDetail['visibility'];
  /** 공개 범위를 바꾼 뒤 상세를 다시 부른다 */
  reload: () => void;
}

export type AuthorActionSlot = (context: AuthorActionContext) => ReactNode;

export interface AuthorActionsProps extends AuthorActionContext {
  /** [공개 범위 ▾] — 004 `VisibilitySelect`(`PUT /api/posts/{postId}/visibility`) 자리 */
  visibilityControl?: AuthorActionSlot;
  /** [삭제] — 006 휴지통 확인 창 + `DELETE /api/posts/{postId}` 자리 */
  deleteControl?: AuthorActionSlot;
}

/**
 * 작성자에게만 보이는 버튼 줄 (005 T059, US4 #6, FR-038, 004 FR-045): [수정](→ `/write/{id}`) · [공개 범위 ▾] · [삭제].
 * 서버 판정이 먼저이고 버튼 숨김은 보조다(원칙 III).
 *
 * (구현 메모) [공개 범위 ▾]는 004 `PUT /api/posts/{postId}/visibility`·`VisibilitySelect`(004 US1), [삭제]는 006
 * `DELETE /api/posts/{postId}`·확인 창이 소유하는데 이 브랜치에 아직 없다 — 그 기능이 `visibilityControl`·
 * `deleteControl`을 채운다. 004 `PostActions`가 생기면 이 줄을 그것으로 바꾼다.
 */
export default function AuthorActions({
  postId,
  visibility,
  reload,
  visibilityControl,
  deleteControl,
}: AuthorActionsProps) {
  const context = { postId, visibility, reload };
  return (
    <div
      data-testid="author-actions"
      style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', margin: '0 0 1rem' }}
    >
      <Link to={`/write/${postId}`}>수정</Link>
      {visibilityControl ? visibilityControl(context) : null}
      {deleteControl ? deleteControl(context) : null}
    </div>
  );
}
