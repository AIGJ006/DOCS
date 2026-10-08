package com.team.blog.discovery.application.trending;

import com.team.blog.discovery.application.CursorPage;
import com.team.blog.discovery.application.PostCardAssembler;
import com.team.blog.discovery.application.PostCardView;
import com.team.blog.discovery.application.ReadingProperties;
import com.team.blog.discovery.application.SnapshotExpiredException;
import com.team.blog.discovery.infra.PostCardQueryRepository;
import com.team.blog.discovery.infra.PostCardRow;
import com.team.blog.discovery.infra.TrendingRepository;
import com.team.blog.discovery.infra.TrendingSnapshotStore;
import com.team.blog.discovery.infra.TrendingSnapshotStore.StoreUnavailableException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 트렌딩 읽기 (012 T027, research R5, contracts §3, FR-010~013).
 *
 * <ul>
 *   <li>첫 요청: {@code trending:current} 스냅샷의 0번째부터. [더 보기]: 커서의 스냅샷·위치부터 — 보는 도중 새 스냅샷이 생겨도 처음 받은
 *       순위를 끝까지 따른다(FR-010).
 *   <li>{@code read-chunk}(18)개씩 {@code LRANGE} → 005 카드 SQL({@link
 *       PostCardQueryRepository#findCardsByIds}, 공용 조건)으로 지금 볼 수 있는 것만 번호 순서대로 담는다. 9 + 1개가 되거나 스냅샷
 *       끝까지 반복한다 — 그 사이 비공개·휴지통·숨김·작성자 유예가 된 글은 건너뛴다(FR-012). 10번째가 있으면 9번째 카드 다음 위치가 {@code
 *       nextCursor}.
 *   <li>커서의 스냅샷이 만료됐으면 410 {@code SNAPSHOT_EXPIRED}(FR-011).
 *   <li>Redis 장애, {@code current} 없음, 첫 요청의 {@code count} 없음 → 계산 SQL을 바로 돌린 첫 9개 + {@code
 *       nextCursor: null}(FR-013). 커서가 있었어도 Redis 장애면 같은 대체 경로다.
 * </ul>
 */
@Service
public class TrendingQueryService {

    private final TrendingSnapshotStore store;
    private final TrendingRepository repository;
    private final PostCardQueryRepository cards;
    private final PostCardAssembler assembler;
    private final TrendingCursor cursors;
    private final TrendingProperties properties;
    private final ReadingProperties reading;
    private final Clock clock;

    public TrendingQueryService(
            TrendingSnapshotStore store,
            TrendingRepository repository,
            PostCardQueryRepository cards,
            PostCardAssembler assembler,
            TrendingCursor cursors,
            TrendingProperties properties,
            ReadingProperties reading,
            Clock clock) {
        this.store = store;
        this.repository = repository;
        this.cards = cards;
        this.assembler = assembler;
        this.cursors = cursors;
        this.properties = properties;
        this.reading = reading;
        this.clock = clock;
    }

    /**
     * @throws com.team.blog.shared.web.cursor.InvalidCursorException 다른 목록의 커서·형식 오류 (400)
     * @throws SnapshotExpiredException 커서의 스냅샷이 만료됨 (410)
     */
    public CursorPage<PostCardView> page(String cursor) {
        TrendingCursor.Key key = cursors.decode(cursor);
        try {
            String snapshotId;
            long position;
            if (key == null) {
                Optional<String> current = store.current();
                if (current.isEmpty()) {
                    return computeNow();
                }
                snapshotId = current.get();
                position = 0;
            } else {
                snapshotId = key.snapshotId();
                position = key.position();
            }
            Optional<Integer> count = store.count(snapshotId);
            if (count.isEmpty()) {
                if (key != null) {
                    throw new SnapshotExpiredException();
                }
                return computeNow();
            }
            return read(snapshotId, position, count.get());
        } catch (StoreUnavailableException e) {
            return computeNow();
        }
    }

    private CursorPage<PostCardView> read(String snapshotId, long start, int count) {
        int pageSize = reading.list().pageSize();
        int need = pageSize + 1;
        List<PostCardRow> visible = new ArrayList<>();
        List<Long> positions = new ArrayList<>();
        long position = start;
        while (visible.size() < need && position < count) {
            List<Long> ids =
                    store.range(snapshotId, position, position + properties.readChunk() - 1);
            if (ids.isEmpty()) {
                break;
            }
            Map<Long, PostCardRow> rows = byId(cards.findCardsByIds(ids));
            for (int i = 0; i < ids.size() && visible.size() < need; i++) {
                PostCardRow row = rows.get(ids.get(i));
                if (row != null) {
                    visible.add(row);
                    positions.add(position + i);
                }
            }
            position += ids.size();
        }
        boolean more = visible.size() > pageSize;
        List<PostCardRow> page = more ? visible.subList(0, pageSize) : visible;
        String nextCursor =
                more
                        ? cursors.encode(
                                new TrendingCursor.Key(snapshotId, positions.get(pageSize - 1) + 1))
                        : null;
        return new CursorPage<>(assembler.toViews(page), nextCursor);
    }

    /** 대체 경로: 계산 SQL로 첫 9개, [더 보기] 없음 (FR-013). */
    private CursorPage<PostCardView> computeNow() {
        List<Long> ids = repository.compute(clock.instant(), reading.list().pageSize());
        Map<Long, PostCardRow> rows = byId(cards.findCardsByIds(ids));
        List<PostCardRow> ordered = new ArrayList<>();
        for (Long id : ids) {
            PostCardRow row = rows.get(id);
            if (row != null) {
                ordered.add(row);
            }
        }
        return new CursorPage<>(assembler.toViews(ordered), null);
    }

    private static Map<Long, PostCardRow> byId(List<PostCardRow> rows) {
        Map<Long, PostCardRow> map = new HashMap<>();
        for (PostCardRow row : rows) {
            map.put(row.id(), row);
        }
        return map;
    }
}
