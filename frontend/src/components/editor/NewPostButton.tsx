import { Link } from 'react-router-dom';

/**
 * [새 글] 버튼 (002 T054, FR-001). `/write/new`로 가면 임시글을 만들고 에디터를 연다.
 * 공통 머리말에 두는 것은 001/005 화면 담당이다.
 */
export default function NewPostButton() {
  return (
    <Link className="new-post-button" to="/write/new">
      새 글
    </Link>
  );
}
