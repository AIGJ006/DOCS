import type { AutosaveStatus } from '../../features/editor/autosaveQueue';
import { statusText } from '../../features/editor/saveStatusText';

/**
 * 저장 상태 (002 T086, FR-013). 색이 아니라 글자로 알리고, 화면 읽기 도구에는 조용히(`aria-live="polite"`) 전한다.
 */
interface Props {
  status: AutosaveStatus | null;
  /** 충돌 상태에서 [비교하기] (비교 창은 US5). 없으면 버튼을 보이지 않는다. */
  onCompare?: () => void;
}

export default function SaveStatus({ status, onCompare }: Props) {
  return (
    <p className="save-status" role="status" aria-live="polite" data-kind={status?.kind ?? 'none'}>
      {status ? statusText(status) : ''}
      {status?.kind === 'conflict' && onCompare ? (
        <>
          {' '}
          <button type="button" onClick={onCompare}>
            비교하기
          </button>
        </>
      ) : null}
    </p>
  );
}
