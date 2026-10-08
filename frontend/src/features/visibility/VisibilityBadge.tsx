import { VISIBILITY_OPTIONS, type VisibilityValue } from './visibilityOptions';
import './visibility.css';

export interface VisibilityBadgeProps {
  visibility: VisibilityValue;
  /**
   * 그림 글자만 보이고 화면 읽기 프로그램용 짧은 글자("공개"·"비공개")를 둔다 — 내 글 관리 줄처럼 자리가 좁은 곳(006 FR-008).
   */
  compact?: boolean;
}

/**
 * 공개 범위 배지 (004 T039, FR-046): 🌐 전체 공개 / 👥 친구 공개 / 🔒 나만 보기. 그림 글자는 화면 읽기 프로그램이 읽지 않는다.
 * 작성자에게만 보이는 자리(내 글 관리·자기 글 상세)에 쓴다.
 */
export default function VisibilityBadge({ visibility, compact = false }: VisibilityBadgeProps) {
  const option = VISIBILITY_OPTIONS[visibility];
  return (
    <span className="visibility-badge" data-visibility={visibility}>
      <span aria-hidden="true">{option.icon}</span>
      {compact ? (
        <span className="sr-only">{option.shortLabel}</span>
      ) : (
        <>
          {' '}
          <span>{option.label}</span>
        </>
      )}
    </span>
  );
}
