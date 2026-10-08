import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import AltTextPanel from '../AltTextPanel';

const A = 'http://localhost:9000/blog/images/2026/10/3f1c2a9e-8d7b-4c1e-9a55-0b6f2a1d7e44.webp';
const B = 'http://localhost:9000/blog/images/2026/10/7a2b4c6d-1e3f-4a5b-8c7d-9e0f1a2b3c4d.webp';

function Harness({ initial }: { initial: string }) {
  const [md, setMd] = useState(initial);
  return (
    <div>
      <AltTextPanel contentMd={md} onChange={setMd} />
      <output data-testid="md">{md}</output>
      <button type="submit">발행</button>
    </div>
  );
}

describe('AltTextPanel', () => {
  it('대체글 없는 사진 수 → [대체글 넣기] → 사진별 미리보기·입력칸·도움말, 입력하면 본문 반영', () => {
    render(<Harness initial={`![](${A})\n\n![](${B})`} />);

    expect(screen.getByText('대체글이 없는 사진이 2장 있어요')).toBeInTheDocument();
    expect(screen.queryByLabelText('사진 1 대체글')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '대체글 넣기' }));

    const images = Array.from(document.querySelectorAll('.alt-text-panel img'));
    expect(images.map((i) => i.getAttribute('src'))).toEqual([A, B]);
    expect(
      screen.getAllByText(
        "사진을 볼 수 없는 분께 읽어 줄 설명이에요. 예: 'fetch join 전후 쿼리 수 비교 그래프'",
      ),
    ).toHaveLength(2);

    fireEvent.change(screen.getByLabelText('사진 2 대체글'), {
      target: { value: '쿼리 수 그래프' },
    });
    expect(screen.getByTestId('md').textContent).toBe(`![](${A})\n\n![쿼리 수 그래프](${B})`);
    // 입력 중에도 입력칸이 사라지지 않는다
    expect(screen.getByLabelText('사진 2 대체글')).toHaveValue('쿼리 수 그래프');
    expect(screen.getByRole('button', { name: '발행' })).toBeEnabled();
  });

  it('125자를 넘으면 안내만 하고 거부하지 않는다', () => {
    render(<Harness initial={`![](${A})`} />);
    fireEvent.click(screen.getByRole('button', { name: '대체글 넣기' }));
    const long = '가'.repeat(126);
    fireEvent.change(screen.getByLabelText('사진 1 대체글'), { target: { value: long } });

    expect(screen.getByText('125자 이내가 읽기 좋아요 (지금 126자)')).toBeInTheDocument();
    expect(screen.getByTestId('md').textContent).toBe(`![${long}](${A})`);
  });

  it('대체글 없는 사진이 없으면 패널이 없다', () => {
    const { container } = render(
      <AltTextPanel contentMd={`![설명](${A})`} onChange={() => undefined} />,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
