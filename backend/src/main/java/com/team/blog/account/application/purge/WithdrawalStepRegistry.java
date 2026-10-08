package com.team.blog.account.application.purge;

import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 등록된 탈퇴 정리 단계 (015 T015, research R9, FR-029). {@link WithdrawalPurgeStep} Bean을 모두 받아 {@code
 * order()} 오름차순으로 둔다. 같은 order가 둘이면 순서가 모호하므로 애플리케이션 시작을 실패시킨다. 시작 때 INFO로 등록된 order를 남긴다.
 */
@Component
public class WithdrawalStepRegistry {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalStepRegistry.class);

    private final List<WithdrawalPurgeStep> steps;

    public WithdrawalStepRegistry(List<WithdrawalPurgeStep> steps) {
        List<WithdrawalPurgeStep> sorted =
                (steps == null ? List.<WithdrawalPurgeStep>of() : steps)
                        .stream()
                                .sorted(Comparator.comparingInt(WithdrawalPurgeStep::order))
                                .toList();
        Map<Integer, String> seen = new HashMap<>();
        for (WithdrawalPurgeStep step : sorted) {
            String previous = seen.putIfAbsent(step.order(), name(step));
            if (previous != null) {
                throw new IllegalStateException(
                        "탈퇴 정리 단계 order가 겹칩니다: order="
                                + step.order()
                                + " ("
                                + previous
                                + ", "
                                + name(step)
                                + ")");
            }
        }
        this.steps = sorted;
        log.info("탈퇴 정리 단계 orders={}", orders());
    }

    /** order 오름차순 단계. */
    public List<WithdrawalPurgeStep> steps() {
        return steps;
    }

    /** 등록된 order (오름차순). */
    public List<Integer> orders() {
        return steps.stream().map(WithdrawalPurgeStep::order).toList();
    }

    /** 필수 단계 중 등록되지 않은 order (오름차순). 비어 있으면 정리 작업이 돌 수 있다. */
    public List<Integer> missingRequired(Collection<Integer> required) {
        Set<Integer> present =
                steps.stream().map(WithdrawalPurgeStep::order).collect(Collectors.toSet());
        return new TreeSet<>(required).stream().filter(o -> !present.contains(o)).toList();
    }

    /** 로그·예외에 쓰는 단계 이름 (프록시면 원래 클래스 이름). */
    public static String name(WithdrawalPurgeStep step) {
        Class<?> type = org.springframework.aop.support.AopUtils.getTargetClass(step);
        return type.getSimpleName();
    }
}
