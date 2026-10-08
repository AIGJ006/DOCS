import { conflictBannerText } from '../../features/editor/conflict';

/**
 * 충돌 배너 (002 T103, FR-021). 편집은 막지 않고, 지금 내용이 이 기기에만 저장된다는 것을 글자로 알린다. 화면 읽기 도구에는
 * `role="alert"`로 바로 전한다.
 */
interface Props {
  savedAt: string;
  onCompare: () => void;
}

export default function ConflictBanner({ savedAt, onCompare }: Props) {
  return (
    <div className="conflict-banner" role="alert">
      <span>{conflictBannerText(savedAt)}</span>{' '}
      <button type="button" onClick={onCompare}>
        비교하기
      </button>
    </div>
  );
}
