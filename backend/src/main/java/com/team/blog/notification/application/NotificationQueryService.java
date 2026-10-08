package com.team.blog.notification.application;

import com.team.blog.notification.infra.NotificationListQueryRepository;
import com.team.blog.notification.infra.NotificationRepository;
import com.team.blog.notification.infra.NotificationRow;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.shared.security.Viewer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 안 읽은 수와 내 알림 목록 (011 research R10·R11). 모든 조회의 첫 조건은 세션 회원({@code receiver_id = :me})이다. 목록은 SQL
 * 1번 (FR-032).
 */
@Service
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationRepository notifications;
    private final NotificationListQueryRepository lists;
    private final NotificationItemAssembler assembler;
    private final NotificationCursor cursors;
    private final NotificationProperties properties;

    public NotificationQueryService(
            NotificationRepository notifications,
            NotificationListQueryRepository lists,
            NotificationItemAssembler assembler,
            NotificationCursor cursors,
            NotificationProperties properties) {
        this.notifications = notifications;
        this.lists = lists;
        this.assembler = assembler;
        this.cursors = cursors;
        this.properties = properties;
    }

    public long unreadCount(long me) {
        return notifications.countUnread(me);
    }

    /**
     * @param size {@code dropdown-size}(10) 또는 {@code page-size}(20). {@code null}이면 {@code
     *     page-size}
     * @throws ValidationException 그 밖의 크기 (400 {@code VALIDATION_FAILED}, 칸 {@code size})
     */
    public NotificationItem.Page page(Viewer viewer, String cursor, Integer size) {
        int n = size == null ? properties.pageSize() : size;
        if (n != properties.dropdownSize() && n != properties.pageSize()) {
            throw ValidationException.of(
                    "size",
                    "INVALID_SIZE",
                    properties.dropdownSize() + " 또는 " + properties.pageSize() + "만 쓸 수 있어요");
        }
        NotificationCursor.Position position = cursors.decode(cursor);
        List<NotificationRow> rows =
                lists.page(
                        viewer.id(),
                        position == null ? null : position.updatedAt(),
                        position == null ? null : position.id(),
                        n + 1,
                        properties.previewScan());
        boolean more = rows.size() > n;
        List<NotificationItem> items = new ArrayList<>(Math.min(rows.size(), n));
        for (NotificationRow row : more ? rows.subList(0, n) : rows) {
            items.add(assembler.assemble(row, viewer));
        }
        String next = null;
        if (more) {
            NotificationRow last = rows.get(n - 1);
            next = cursors.next(last.updatedAt(), last.id());
        }
        return new NotificationItem.Page(items, next);
    }
}
