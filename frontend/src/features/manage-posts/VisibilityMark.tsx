import type { Visibility } from '../../api/posts';

/**
 * 제목 앞 공개 범위 표시 (FR-008): 🌐/🔒 + 스크린 리더용 글자 "공개"/"비공개". 그림 글자는 읽지 않는다.
 *
 * (구현 메모) 004 `features/visibility/VisibilityBadge.tsx`가 이 브랜치에 아직 없어 006 안에 최소 표시만 둔다. 004가
 * 생기면 그 컴포넌트로 바꾼다.
 */
export default function VisibilityMark({ visibility }: { visibility: Visibility }) {
  const isPublic = visibility === 'PUBLIC';
  return (
    <span className="manage-visibility" data-visibility={visibility}>
      <span aria-hidden="true">{isPublic ? '🌐' : '🔒'}</span>
      <span className="sr-only">{isPublic ? '공개' : '비공개'}</span>
    </span>
  );
}
