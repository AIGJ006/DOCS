import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getPostCategory, listMyCategories, setPostCategory } from '../../api/categories';
import { flattenCategories, type CategoryOption } from './categoryTree';
import './category.css';

/** 하위 카테고리 들여쓰기 (전각 공백 — option 안에서는 CSS 여백이 먹지 않는다) */
const INDENT = '\u3000';

export const CATEGORY_SAVE_FAILED_TEXT = '카테고리를 저장하지 못했어요';

/**
 * 글쓰기 화면 제목 위의 카테고리 선택 (017 US2, FR-023). 고르는 즉시 `PUT /api/posts/{id}/category`로 저장한다 —
 * 발행·자동 저장과 별개다. 실패하면 이전 값으로 돌리고 알린다. 목록·현재 값을 못 불러와도 글쓰기는 막지 않는다(선택만 숨김).
 */
export default function CategorySelect({ postId }: { postId: number }) {
  const [options, setOptions] = useState<CategoryOption[] | null>(null);
  const [value, setValue] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([listMyCategories(), getPostCategory(postId)]).then(
      ([mine, current]) => {
        if (!cancelled) {
          setOptions(flattenCategories(mine.items));
          setValue(current.categoryId);
        }
      },
      () => {
        // 불러오지 못하면 선택을 보이지 않는다 (글쓰기는 계속)
      },
    );
    return () => {
      cancelled = true;
    };
  }, [postId]);

  if (options === null) {
    return null;
  }

  async function change(next: number | null) {
    const before = value;
    setValue(next);
    setSaving(true);
    setError(null);
    try {
      const saved = await setPostCategory(postId, next);
      setValue(saved.categoryId);
    } catch {
      setValue(before);
      setError(CATEGORY_SAVE_FAILED_TEXT);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="category-select">
      <label htmlFor="editor-category">카테고리</label>
      <select
        id="editor-category"
        value={value === null ? '' : String(value)}
        disabled={saving}
        onChange={(event) =>
          void change(event.target.value === '' ? null : Number(event.target.value))
        }
      >
        <option value="">분류 없음</option>
        {options.map((option) => (
          <option key={option.id} value={option.id}>
            {option.depth === 1 ? `${INDENT}└ ${option.name}` : option.name}
          </option>
        ))}
      </select>
      <Link to="/manage/categories">카테고리 관리</Link>
      {error ? (
        <span role="alert" className="category-select__error">
          {error}
        </span>
      ) : null}
    </div>
  );
}
