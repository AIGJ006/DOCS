import { act, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { FieldError } from '../../../api/client';
import TagInput from '../TagInput';

function Harness({
  initialTags = [],
  max = 10,
  errors = [],
  onChange,
}: {
  initialTags?: string[];
  max?: number;
  errors?: FieldError[];
  onChange?: (tags: string[]) => void;
}) {
  const [tags, setTags] = useState(initialTags);
  return (
    <TagInput
      value={tags}
      max={max}
      errors={errors}
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
