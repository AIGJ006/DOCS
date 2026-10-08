import { act, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { FieldError } from '../../../api/client';
import type { TagSuggestion } from '../../../api/types/tags';
import type { SuggestLoader } from '../../../features/tag/useTagSuggest';
import TagInput from '../TagInput';

const noSuggestions: SuggestLoader = () => Promise.resolve([]);

function Harness({
  initialTags = [],
  max = 10,
  errors = [],
  onChange,
  loadSuggestions = noSuggestions,
}: {
  initialTags?: string[];
  max?: number;
  errors?: FieldError[];
  onChange?: (tags: string[]) => void;
  loadSuggestions?: SuggestLoader;
}) {
  const [tags, setTags] = useState(initialTags);
  return (
    <TagInput
      value={tags}
      max={max}
      errors={errors}
      loadSuggestions={loadSuggestions}
      onChange={(next) => {
        setTags(next);
        onChange?.(next);
      }}
    />
  );
}

function chips() {
  return within(screen.getByRole('list', { name: '붙인 태그' }))
    .queryAllByRole('listitem')
    .map((li) => li.getAttribute('data-tag'));
}

describe('TagInput (008 US1)', () => {
  it('Enter·쉼표로 추가하고 띄어쓰기로 나누지 않으며 정규화한 모양으로 보인다', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const input = screen.getByLabelText('태그 입력');

    await user.type(input, 'Spring Boot{Enter}');
    expect(chips()).toEqual(['spring-boot']);
    expect(screen.getByText('#spring-boot')).toBeInTheDocument();

    await user.type(input, 'C#,');
    expect(chips()).toEqual(['spring-boot', 'c#']);
    expect(input).toHaveValue('');
  });

  it('같은 이름은 추가하지 않고 기존 칩을 강조한다', async () => {
    const user = userEvent.setup();
    render(<Harness initialTags={['spring-boot', 'jpa']} />);

    await user.type(screen.getByLabelText('태그 입력'), '#SPRING  BOOT{Enter}');

    expect(chips()).toEqual(['spring-boot', 'jpa']);
    expect(screen.getByText('#spring-boot').closest('li')).toHaveAttribute('data-flash', 'true');
  });

  it('형식 오류는 칩 없이 문구를 보이고 입력을 지우지 않는다', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const input = screen.getByLabelText('태그 입력');

    await user.type(input, '🔥hot{Enter}');

    expect(chips()).toEqual([]);
    expect(screen.getByText('쓸 수 없는 글자가 있어요')).toBeInTheDocument();
    expect(input).toHaveValue('🔥hot');
    expect(input).toHaveAttribute('aria-invalid', 'true');

    await user.clear(input);
    await user.type(input, 'a'.repeat(31) + '{Enter}');
    expect(screen.getByText('태그는 30자까지 쓸 수 있어요')).toBeInTheDocument();
  });

  it('× 클릭과 빈 칸 Backspace로 지운다', async () => {
    const user = userEvent.setup();
    render(<Harness initialTags={['spring', 'jpa', 'java']} />);

    await user.click(screen.getByRole('button', { name: 'jpa 태그 빼기' }));
    expect(chips()).toEqual(['spring', 'java']);

    await user.click(screen.getByLabelText('태그 입력'));
    await user.keyboard('{Backspace}');
    expect(chips()).toEqual(['spring']);

    await user.type(screen.getByLabelText('태그 입력'), 'ab{Backspace}');
    expect(chips()).toEqual(['spring']);
  });

  it('한글 조합 중 Enter는 태그를 넣지 않는다', () => {
    render(<Harness />);
    const input = screen.getByLabelText('태그 입력');
    fireEvent.change(input, { target: { value: '스프' } });
    fireEvent.keyDown(input, { key: 'Enter', isComposing: true });
    expect(chips()).toEqual([]);
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(chips()).toEqual(['스프']);
  });

  it('Alt+←/→로 순서를 바꾸고 화면 읽기 프로그램에 알린다', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Harness initialTags={['spring', 'jpa', 'java']} onChange={onChange} />);

    const jpa = screen.getByText('#jpa').closest('li')!;
    jpa.focus();
    await user.keyboard('{Alt>}{ArrowLeft}{/Alt}');

    expect(chips()).toEqual(['jpa', 'spring', 'java']);
    expect(screen.getByRole('status')).toHaveTextContent('jpa 태그를 1번째로 옮겼어요');
    expect(screen.getByText('#jpa').closest('li')).toHaveFocus();

    await user.keyboard('{Alt>}{ArrowRight}{/Alt}{Alt>}{ArrowRight}{/Alt}');
    expect(chips()).toEqual(['spring', 'java', 'jpa']);
    expect(screen.getByRole('status')).toHaveTextContent('jpa 태그를 3번째로 옮겼어요');

    await user.keyboard('{Alt>}{ArrowRight}{/Alt}');
    expect(chips()).toEqual(['spring', 'java', 'jpa']);
  });

  it('끌어서 순서를 바꾼다', () => {
    render(<Harness initialTags={['spring', 'jpa', 'java']} />);
    const java = screen.getByText('#java').closest('li')!;
    const spring = screen.getByText('#spring').closest('li')!;
    const data = new Map<string, string>();
    const dataTransfer = {
      setData: (k: string, v: string) => data.set(k, v),
      getData: (k: string) => data.get(k) ?? '',
      effectAllowed: 'move',
      dropEffect: 'move',
    };

    fireEvent.dragStart(java, { dataTransfer });
    fireEvent.dragOver(spring, { dataTransfer });
    fireEvent.drop(spring, { dataTransfer });

    expect(chips()).toEqual(['java', 'spring', 'jpa']);
  });

  it('개수 "2 / 10"을 입력칸에 연결하고 최대 개수면 입력을 막는다', async () => {
    const user = userEvent.setup();
    render(<Harness initialTags={['a', 'b']} max={3} />);
    const input = screen.getByLabelText('태그 입력');
    const counter = screen.getByText('2 / 3');
    expect(input.getAttribute('aria-describedby')).toContain(counter.id);

    await user.type(input, 'c{Enter}');
    expect(screen.getByText('3 / 3')).toBeInTheDocument();
    expect(input).toBeDisabled();
    expect(input).toHaveAttribute('placeholder', '태그는 3개까지예요');
  });

  it('errors의 tags[i]는 i번째 칩을 오류 칩(⚠ + 문구)으로, 다른 칩은 정상', () => {
    render(
      <Harness
        initialTags={['spring', 'jpa', 'bad-word']}
        errors={[
          { field: 'tags[2]', code: 'TAG_BANNED_WORD', message: '쓸 수 없는 단어가 들어 있어요' },
          { field: 'title', code: 'TITLE_REQUIRED', message: '제목을 입력해 주세요' },
        ]}
      />,
    );
    const bad = screen.getByText('#bad-word').closest('li')!;
    expect(bad).toHaveAttribute('aria-invalid', 'true');
    expect(bad).toHaveTextContent('⚠');
    expect(bad).toHaveTextContent('쓸 수 없는 단어가 들어 있어요');
    expect(screen.getByText('#spring').closest('li')).not.toHaveAttribute('aria-invalid');
    expect(screen.queryByText('제목을 입력해 주세요')).not.toBeInTheDocument();
  });

  it('initialTags로 미리 채운다', () => {
    render(<Harness initialTags={['node.js', 'c#', '스프링-부트']} />);
    expect(chips()).toEqual(['node.js', 'c#', '스프링-부트']);
    expect(screen.getByText('3 / 10')).toBeInTheDocument();
  });

  it('강조는 잠시 뒤 사라진다', () => {
    vi.useFakeTimers();
    try {
      render(<Harness initialTags={['jpa']} />);
      const input = screen.getByLabelText('태그 입력');
      fireEvent.change(input, { target: { value: 'JPA' } });
      fireEvent.keyDown(input, { key: 'Enter' });
      expect(screen.getByText('#jpa').closest('li')).toHaveAttribute('data-flash', 'true');
      act(() => {
        vi.advanceTimersByTime(2000);
      });
      expect(screen.getByText('#jpa').closest('li')).not.toHaveAttribute('data-flash');
    } finally {
      vi.useRealTimers();
    }
  });
});

describe('TagInput 자동완성 (008 US3)', () => {
  const SUGGESTIONS: TagSuggestion[] = [
    { name: 'spring-boot', postCount: 3, mine: true },
    { name: 'spring', postCount: 30, mine: false },
    { name: 'spring-data', postCount: 2, mine: false },
  ];

  function loader() {
    return vi.fn<SuggestLoader>(() => Promise.resolve(SUGGESTIONS));
  }

  it('0.3초 멈추면 listbox에 "#이름 · 수 · 내 태그"로 보이고 ↑↓·Enter로 고른다', async () => {
    const load = loader();
    const user = userEvent.setup();
    render(<Harness loadSuggestions={load} />);
    const input = screen.getByRole('combobox', { name: '태그 입력' });

    await user.type(input, 'spr');
    const listbox = await screen.findByRole('listbox', { name: '태그 추천' });
    const options = within(listbox).getAllByRole('option');
    expect(options.map((o) => o.textContent)).toEqual([
      '#spring-boot · 3 · 내 태그',
      '#spring · 30',
      '#spring-data · 2',
    ]);
    expect(input).toHaveAttribute('aria-expanded', 'true');
    expect(load).toHaveBeenCalledWith('spr', expect.anything());

    await user.keyboard('{ArrowDown}{ArrowDown}');
    expect(options[1]).toHaveAttribute('aria-selected', 'true');
    expect(input).toHaveAttribute('aria-activedescendant', options[1].id);
    await user.keyboard('{ArrowUp}');
    expect(options[0]).toHaveAttribute('aria-selected', 'true');
    await user.keyboard('{ArrowDown}{Enter}');

    expect(chips()).toEqual(['spring']);
    expect(input).toHaveValue('');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('Esc로 닫고, 고르지 않은 Enter는 입력값을 그대로 새 태그로 넣는다', async () => {
    const user = userEvent.setup();
    render(<Harness loadSuggestions={loader()} />);
    const input = screen.getByRole('combobox', { name: '태그 입력' });

    await user.type(input, 'spr');
    await screen.findByRole('listbox');
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(input).toHaveAttribute('aria-expanded', 'false');

    await user.type(input, 'ing');
    await screen.findByRole('listbox');
    await user.keyboard('{Enter}');
    expect(chips()).toEqual(['spring']);
  });

  it('클릭으로 고르고, 이미 붙인 태그는 후보에서 뺀다', async () => {
    const user = userEvent.setup();
    render(<Harness initialTags={['spring-boot']} loadSuggestions={loader()} />);

    await user.type(screen.getByRole('combobox', { name: '태그 입력' }), 'spr');
    const listbox = await screen.findByRole('listbox');
    const options = within(listbox).getAllByRole('option');
    expect(options.map((o) => o.textContent)).toEqual(['#spring · 30', '#spring-data · 2']);

    await user.click(options[1]);
    expect(chips()).toEqual(['spring-boot', 'spring-data']);
  });

  it('한글 조합 중에는 부르지 않는다', async () => {
    const load = loader();
    render(<Harness loadSuggestions={load} />);
    const input = screen.getByRole('combobox', { name: '태그 입력' });

    fireEvent.compositionStart(input);
    fireEvent.change(input, { target: { value: '스프' } });
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 400));
    });
    expect(load).not.toHaveBeenCalled();

    fireEvent.compositionEnd(input);
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 400));
    });
    expect(load).toHaveBeenCalledWith('스프', expect.anything());
  });
});
