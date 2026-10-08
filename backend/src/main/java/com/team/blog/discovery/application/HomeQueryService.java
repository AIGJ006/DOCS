package com.team.blog.discovery.application;

import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.cursor.ListScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 홈(전체 글 목록) 조회 (005 T022, US1, FR-001~006). */
@Service
@Transactional(readOnly = true)
public class HomeQueryService {

    private final PostListService lists;

    public HomeQueryService(PostListService lists) {
        this.lists = lists;
    }

    /**
     * @param cursor 이전 응답의 {@code nextCursor} (첫 페이지면 {@code null})
     */
    public CursorPage<PostCardView> listHome(String cursor, Viewer viewer) {
        return lists.page(ListScope.home(), CardFilter.all(), cursor, viewer);
    }
}
