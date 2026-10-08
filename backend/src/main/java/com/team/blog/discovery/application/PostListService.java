package com.team.blog.discovery.application;

import com.team.blog.discovery.application.PostListCursor.CursorKey;
import com.team.blog.discovery.infra.PostCardQueryRepository;
import com.team.blog.discovery.infra.PostCardRow;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.cursor.ListScope;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커서 목록 한 페이지 만들기 (005 T022·T048 공용, FR-003~006, research R-04·R-05).
 *
 * <p>page-size + 1개를 읽어 더 있는지 보고(빈 [더 보기] 요청이 생기지 않는다), 넘치면 앞 page-size개 + 마지막 카드의 {@code
 * (first_public_at, id)}를 커서로 돌려준다. 클라이언트가 보낸 {@code size}는 쓰지 않는다(원칙 VII).
 */
@Service
@Transactional(readOnly = true)
public class PostListService {

    private final PostCardQueryRepository cards;
    private final PostCardAssembler assembler;
    private final PostListCursor cursors;
    private final ReadingProperties properties;

    public PostListService(
            PostCardQueryRepository cards,
            PostCardAssembler assembler,
            PostListCursor cursors,
            ReadingProperties properties) {
        this.cards = cards;
        this.assembler = assembler;
        this.cursors = cursors;
        this.properties = properties;
    }

    /**
     * @param scope 목록 구분 (커서가 다른 목록에서 왔는지 보는 기준)
     * @param filter 블로그 주인·태그 조건 (전체 목록이면 {@link CardFilter#all()})
     * @param cursor 클라이언트가 돌려준 위치 값 (첫 페이지면 {@code null})
     */
    public CursorPage<PostCardView> page(
            ListScope scope, CardFilter filter, String cursor, Viewer viewer) {
        int pageSize = properties.list().pageSize();
        CursorKey after = cursors.decode(cursor, scope);
        List<PostCardRow> rows = cards.findCards(viewer, filter, after, pageSize + 1);
        boolean more = rows.size() > pageSize;
        List<PostCardRow> page = more ? rows.subList(0, pageSize) : rows;
        String nextCursor = null;
        if (more) {
            PostCardRow last = page.get(page.size() - 1);
            nextCursor = cursors.encode(scope, last.firstPublicAt(), last.id());
        }
        return new CursorPage<>(assembler.toViews(page), nextCursor);
    }
}
