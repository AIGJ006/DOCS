package com.team.blog.interaction.application;

import com.team.blog.post.application.PostCounterService;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 좋아요 수 보정 배치 (009 T039, FR-005, SC-003, 30 §4-1). 매일 {@code blog.like.reconcile-cron}에 {@code
 * post.like_count}를 {@code post_like} 실제 건수와 맞춘다. 정상이면 고칠 것이 없어야 하므로, 고친 글이 있으면 WARN으로 글 번호(최대
 * 20개)를 남긴다. ShedLock {@code like-reconcile}.
 */
@Component
public class LikeReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(LikeReconcileJob.class);
    private static final int LOGGED_IDS = 20;

    private final PostCounterService counters;

    public LikeReconcileJob(PostCounterService counters) {
        this.counters = counters;
    }

    @Scheduled(cron = "${blog.like.reconcile-cron}", zone = "${blog.time-zone}")
    @SchedulerLock(name = "like-reconcile", lockAtMostFor = "PT30M")
    public void run() {
        reconcile();
    }

    /**
     * @return 고친 글 번호 (번호 순)
     */
    public List<Long> reconcile() {
        List<Long> fixed = counters.reconcileLikeCounts();
        if (!fixed.isEmpty()) {
            log.warn(
                    "좋아요 수가 실제와 달라 고쳤습니다: {}건, 글 번호 {}{}",
                    fixed.size(),
                    fixed.subList(0, Math.min(LOGGED_IDS, fixed.size())),
                    fixed.size() > LOGGED_IDS ? " 외" : "");
        }
        return fixed;
    }
}
