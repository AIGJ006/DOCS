package com.team.blog.post.application;

import com.team.blog.post.infra.PostMaintenanceRepository;
import com.team.blog.post.infra.PostMaintenanceRepository.RerenderTarget;
import com.team.blog.shared.application.markdown.ContentRenderer;
import com.team.blog.shared.application.markdown.ContentTooComplexException;
import com.team.blog.shared.application.markdown.ImageContext;
import com.team.blog.shared.application.markdown.MarkdownProperties;
import com.team.blog.shared.application.markdown.RenderVersion;
import com.team.blog.shared.application.markdown.RenderedContent;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 다시 렌더링 배치 (002 T116, FR-048, US7 #1, A-11, B-10). 렌더링 규칙 버전({@link RenderVersion#CURRENT})이 오르면
 * 그보다 낮은 발행 글을 {@code blog.markdown.rerender-batch-size}개씩 원문에서 다시 렌더링해 {@code content_html}·{@code
 * excerpt}·{@code render_version}만 바꾼다. 수정 시각·편집 버전은 그대로이고 이벤트도 없다.
 *
 * <ul>
 *   <li>렌더링은 트랜잭션 밖, 사진 판별은 글 작성자 기준.
 *   <li>읽은 뒤 사용자가 다시 발행한 글은 버전 조건으로 0행 — 이번 회차는 건너뛴다(새 발행이 이미 현재 규칙으로 렌더링한다).
 *   <li>{@code CONTENT_TOO_COMPLEX}·그 밖의 실패 글은 경고 로그(postId만) 후 건너뛰고, 남은 건수 게이지에 남는다.
 * </ul>
 */
@Component
public class RerenderJob {

    private static final Logger log = LoggerFactory.getLogger(RerenderJob.class);

    private final PostMaintenanceRepository maintenance;
    private final ContentRenderer renderer;
    private final MarkdownProperties properties;
    private final PostAuthoringMetrics metrics;

    public RerenderJob(
            PostMaintenanceRepository maintenance,
            ContentRenderer renderer,
            MarkdownProperties properties,
            PostAuthoringMetrics metrics) {
        this.maintenance = maintenance;
        this.renderer = renderer;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(
            fixedDelayString = "${blog.markdown.rerender-interval}",
            initialDelayString = "${blog.markdown.rerender-interval}")
    @SchedulerLock(name = "post-rerender")
    public void run() {
        rerender(RenderVersion.CURRENT);
    }

    /**
     * 규칙 버전 {@code current}보다 낮은 발행 글을 모두 한 번씩 다시 렌더링한다.
     *
     * @return 이번 회차에 바꾼 글 수
     */
    public int rerender(int current) {
        int batch = properties.rerenderBatchSize();
        long afterId = 0;
        int updated = 0;
        int skipped = 0;
        for (; ; ) {
            List<RerenderTarget> targets = maintenance.findRerenderTargets(current, afterId, batch);
            if (targets.isEmpty()) {
                break;
            }
            for (RerenderTarget target : targets) {
                afterId = target.id();
                if (rerenderOne(target, current)) {
                    updated++;
                } else {
                    skipped++;
                }
            }
            if (targets.size() < batch) {
                break;
            }
        }
        long remaining = updated + skipped == 0 ? 0 : maintenance.countRerenderTargets(current);
        metrics.rerenderRemaining(remaining);
        if (updated + skipped > 0) {
            log.info("다시 렌더링: 바꿈 {}건, 건너뜀 {}건, 남음 {}건", updated, skipped, remaining);
        }
        return updated;
    }

    private boolean rerenderOne(RerenderTarget target, int current) {
        RenderedContent rendered;
        try {
            rendered = renderer.render(target.contentMd(), new ImageContext(target.authorId()));
        } catch (ContentTooComplexException e) {
            log.warn("다시 렌더링 건너뜀(너무 복잡한 본문): postId={}", target.id());
            return false;
        } catch (RuntimeException e) {
            log.warn("다시 렌더링 실패, 건너뜁니다: postId={} {}", target.id(), e.getClass().getSimpleName());
            return false;
        }
        int rows =
                maintenance.updateRendered(
                        target.id(),
                        rendered.html(),
                        rendered.excerpt(),
                        current,
                        target.editVersion());
        return rows == 1;
    }
}
