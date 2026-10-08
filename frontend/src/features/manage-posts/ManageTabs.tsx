import type { ManageCounts, ManageTab } from '../../api/managePosts';

const TABS: { tab: ManageTab; label: string; count: keyof ManageCounts }[] = [
  { tab: 'drafts', label: '임시글', count: 'drafts' },
  { tab: 'published', label: '발행 글', count: 'published' },
  { tab: 'trash', label: '휴지통', count: 'trash' },
];

export interface ManageTabsProps {
  current: ManageTab;
  counts: ManageCounts | null;
  onSelect: (tab: ManageTab) => void;
}

/** 탭 "임시글 3 · 발행 글 24 · 휴지통 1" (006 T047, FR-003·004). 선택 탭은 `aria-selected`. */
export default function ManageTabs({ current, counts, onSelect }: ManageTabsProps) {
  return (
    <div className="manage-tabs" role="tablist" aria-label="내 글">
      {TABS.map(({ tab, label, count }) => (
        <button
          key={tab}
          type="button"
          role="tab"
          id={`manage-tab-${tab}`}
          aria-selected={tab === current}
          aria-controls="manage-panel"
          tabIndex={tab === current ? 0 : -1}
          onClick={() => onSelect(tab)}
        >
          {counts ? `${label} ${counts[count]}` : label}
        </button>
      ))}
    </div>
  );
}
