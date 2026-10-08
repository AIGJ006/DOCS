import {
  useEffect,
  useId,
  useRef,
  useState,
  type DragEvent,
  type KeyboardEvent as ReactKeyboardEvent,
} from 'react';
import type { FieldError } from '../../api/client';
import { normalizeTag } from '../../features/tag/normalizeTag';
import { tagMessage } from '../../features/tag/tagMessages';
import { useTagSuggest, type SuggestLoader } from '../../features/tag/useTagSuggest';
import '../../features/tag/tag.css';

/**
 * 발행 설정 창의 태그 입력 (008 T024, research R12, FR-020).
 *
 * - 칩은 **정규화된 이름**이다(`normalizeTag`). Enter·쉼표로 넣고, 띄어쓰기로는 나누지 않는다(`Spring Boot` → `#spring-boot`).
 *   한글 조합 중 Enter는 무시한다.
 * - 이미 있는 이름이면 넣지 않고 그 칩을 잠깐 강조한다. 형식 오류면 칩을 만들지 않고 입력칸 아래에 문구를 보이며 입력을 지우지 않는다.
 * - × 버튼·빈 입력칸 Backspace로 빼고, 끌어서(HTML 드래그) 또는 칩에 초점을 두고 Alt+←/→로 순서를 바꾼다(`aria-live` 안내).
 * - "2 / 10"은 입력칸의 `aria-describedby`. 최대 개수면 입력을 막는다.
 * - 자동완성(US3): 입력이 0.3초 멈추고 한글 조합 중이 아닐 때 후보를 `role="listbox"`로 띄운다(`useTagSuggest`). ↑↓로 고르고
 *   Enter로 넣으며 Esc로 닫는다. 아무것도 고르지 않은 Enter는 입력값을 그대로 새 태그로 넣는다. 이미 붙인 태그는 후보에서 뺀다.
 * - 금칙어는 화면이 모른다 — 서버 400의 `tags[i]`가 i번째 칩을 오류 칩(테두리 + "⚠" + 문구)으로 만든다. 칩 순서 = 보낸 순서라
 *   번호가 맞는다. 색만으로 오류를 구분하지 않는다.
 */
export interface TagInputProps {
  /** 지금 칩 (정규화된 이름, 순서 그대로 발행에 보낸다) */
  value: string[];
  onChange: (tags: string[]) => void;
  /** 최대 개수 (`blog.post.max-tags`) */
  max: number;
  /** 서버 400 칸 오류 (`tags[i]`만 쓴다) */
  errors?: FieldError[];
  disabled?: boolean;
  /** 자동완성 불러오기 (기본 `GET /api/tags/suggest`). 시험에서 바꿔 끼운다 */
  loadSuggestions?: SuggestLoader;
}

const FLASH_MS = 1200;

export default function TagInput({
  value,
  onChange,
  max,
  errors = [],
  disabled,
  loadSuggestions,
}: TagInputProps) {
  const [text, setText] = useState('');
  const [composing, setComposing] = useState(false);
  /** Esc로 닫은 입력값 — 입력이 바뀌면 다시 열린다 */
  const [dismissedFor, setDismissedFor] = useState<string | null>(null);
  /** 고른 후보 (그 입력값에 묶는다 — 입력이 바뀌면 선택이 풀린다) */
  const [active, setActive] = useState<{ text: string; index: number } | null>(null);
  const [inputError, setInputError] = useState<string | null>(null);
  const [flash, setFlash] = useState<string | null>(null);
  const [announcement, setAnnouncement] = useState('');
  const dragIndex = useRef<number | null>(null);
  const chipRefs = useRef(new Map<string, HTMLLIElement>());
  const focusAfterMove = useRef<string | null>(null);
  const baseId = useId();
  const countId = `${baseId}-count`;
  const errorId = `${baseId}-error`;
  const listboxId = `${baseId}-suggestions`;

  const full = value.length >= max;
  const suggestions = useTagSuggest(text, composing, loadSuggestions).filter(
    (s) => !value.includes(s.name),
  );
  const open = suggestions.length > 0 && dismissedFor !== text && !full && !disabled;
  const activeIndex =
    open && active !== null && active.text === text && active.index < suggestions.length
      ? active.index
      : -1;
  const optionId = (index: number) => `${baseId}-option-${index}`;

  useEffect(() => {
    if (flash === null) {
      return undefined;
    }
    const timer = setTimeout(() => setFlash(null), FLASH_MS);
    return () => clearTimeout(timer);
  }, [flash]);

  useEffect(() => {
    const name = focusAfterMove.current;
    if (name !== null) {
      focusAfterMove.current = null;
      chipRefs.current.get(name)?.focus();
    }
  }, [value]);

  function add(raw: string) {
    const result = normalizeTag(raw);
    if (!result.ok) {
      setInputError(tagMessage(result.code));
      return;
    }
    setInputError(null);
    setText('');
    if (value.includes(result.name)) {
      setFlash(result.name);
      return;
    }
    if (full) {
      return;
    }
    onChange([...value, result.name]);
  }

  function remove(index: number) {
    onChange(value.filter((_, i) => i !== index));
  }

  function move(from: number, to: number) {
    if (to < 0 || to >= value.length || from === to) {
      return;
    }
    const next = [...value];
    const [name] = next.splice(from, 1);
    next.splice(to, 0, name);
    focusAfterMove.current = name;
    onChange(next);
    setAnnouncement(`${name} 태그를 ${to + 1}번째로 옮겼어요`);
  }

  function onInputKeyDown(event: ReactKeyboardEvent<HTMLInputElement>) {
    if (event.nativeEvent.isComposing) {
      return;
    }
    if (open && (event.key === 'ArrowDown' || event.key === 'ArrowUp')) {
      event.preventDefault();
      const step = event.key === 'ArrowDown' ? 1 : -1;
      const start = activeIndex === -1 ? (step === 1 ? -1 : 0) : activeIndex;
      const next = (start + step + suggestions.length) % suggestions.length;
      setActive({ text, index: next });
      return;
    }
    if (event.key === 'Escape' && open) {
      event.preventDefault();
      setDismissedFor(text);
      return;
    }
    if (event.key === 'Enter' && activeIndex !== -1) {
      event.preventDefault();
      add(suggestions[activeIndex].name);
      return;
    }
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      if (text.trim() !== '') {
        add(text);
      }
      return;
    }
    if (event.key === 'Backspace' && text === '' && value.length > 0) {
      event.preventDefault();
      remove(value.length - 1);
    }
  }

  function onChipKeyDown(event: ReactKeyboardEvent<HTMLLIElement>, index: number) {
    if (!event.altKey) {
      return;
    }
    if (event.key === 'ArrowLeft') {
      event.preventDefault();
      move(index, index - 1);
    } else if (event.key === 'ArrowRight') {
      event.preventDefault();
      move(index, index + 1);
    }
  }

  function onDragStart(event: DragEvent<HTMLLIElement>, index: number) {
    dragIndex.current = index;
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', String(index));
  }

  function onDrop(event: DragEvent<HTMLLIElement>, index: number) {
    event.preventDefault();
    const raw = event.dataTransfer.getData('text/plain');
    const from = dragIndex.current ?? (raw === '' ? null : Number(raw));
    dragIndex.current = null;
    if (from !== null && Number.isInteger(from)) {
      move(from, index);
    }
  }

  function chipError(index: number): FieldError | undefined {
    return errors.find((e) => e.field === `tags[${index}]`);
  }

  const describedBy = [countId, inputError ? errorId : null].filter(Boolean).join(' ');

  return (
    <div className="tag-input">
      <ul className="tag-input__chips" aria-label="붙인 태그">
        {value.map((name, index) => {
          const error = chipError(index);
          return (
            <li
              key={name}
              ref={(el) => {
                if (el) {
                  chipRefs.current.set(name, el);
                } else {
                  chipRefs.current.delete(name);
                }
              }}
              className="tag-input__chip"
              data-tag={name}
              data-flash={flash === name ? 'true' : undefined}
              aria-invalid={error ? 'true' : undefined}
              tabIndex={0}
              draggable={!disabled}
              aria-roledescription="옮길 수 있는 태그"
              onKeyDown={(event) => onChipKeyDown(event, index)}
              onDragStart={(event) => onDragStart(event, index)}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => onDrop(event, index)}
            >
              <span className="tag-input__name">#{name}</span>
              <button
                type="button"
                className="tag-input__remove"
                aria-label={`${name} 태그 빼기`}
                disabled={disabled}
                onClick={() => remove(index)}
              >
                ×
              </button>
              {error ? (
                <span className="tag-input__chip-error">
                  <span aria-hidden="true">⚠ </span>
                  {tagMessage(error.code, error.message)}
                </span>
              ) : null}
            </li>
          );
        })}
      </ul>
      <div className="tag-input__row">
        <input
          className="tag-input__field"
          role="combobox"
          aria-label="태그 입력"
          aria-describedby={describedBy}
          aria-invalid={inputError ? 'true' : undefined}
          aria-autocomplete="list"
          aria-expanded={open}
          aria-controls={listboxId}
          aria-activedescendant={activeIndex === -1 ? undefined : optionId(activeIndex)}
          onCompositionStart={() => setComposing(true)}
          onCompositionEnd={() => setComposing(false)}
          onBlur={() => setDismissedFor(text)}
          onFocus={() => setDismissedFor(null)}
          value={text}
          disabled={disabled || full}
          placeholder={full ? `태그는 ${max}개까지예요` : 'Enter나 쉼표로 추가'}
          onChange={(event) => {
            setText(event.target.value);
            if (inputError) {
              setInputError(null);
            }
          }}
          onKeyDown={onInputKeyDown}
        />
        <span id={countId} className="tag-input__count">
          {value.length} / {max}
        </span>
        <ul
          id={listboxId}
          role="listbox"
          aria-label="태그 추천"
          className="tag-input__suggestions"
          hidden={!open}
        >
          {open
            ? suggestions.map((suggestion, index) => (
                <li
                  key={suggestion.name}
                  id={optionId(index)}
                  role="option"
                  aria-selected={index === activeIndex}
                  className="tag-input__suggestion"
                  // 입력칸의 초점을 잃지 않게 mousedown에서 막는다
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => add(suggestion.name)}
                >
                  #{suggestion.name} · {suggestion.postCount.toLocaleString('ko-KR')}
                  {suggestion.mine ? ' · 내 태그' : ''}
                </li>
              ))
            : null}
        </ul>
      </div>
      {inputError ? (
        <p id={errorId} className="tag-input__error">
          {inputError}
        </p>
      ) : null}
      <p role="status" aria-live="polite" className="visually-hidden">
        {announcement}
      </p>
    </div>
  );
}
