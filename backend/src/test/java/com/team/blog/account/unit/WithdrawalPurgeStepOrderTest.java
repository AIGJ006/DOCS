package com.team.blog.account.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.account.application.purge.WithdrawalStepRegistry;
import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 정리 단계 순서·중복·필수 단계 확인 (015 T006, research R9, FR-029). */
class WithdrawalPurgeStepOrderTest {

    record Step(int order) implements WithdrawalPurgeStep {
        @Override
        public void purge(long memberId) {}
    }

    @Test
    void 단계는_order_오름차순() {
        WithdrawalStepRegistry registry =
                new WithdrawalStepRegistry(
                        List.of(new Step(90), new Step(10), new Step(45), new Step(20)));
        assertThat(registry.steps())
                .extracting(WithdrawalPurgeStep::order)
                .containsExactly(10, 20, 45, 90);
        assertThat(registry.orders()).containsExactly(10, 20, 45, 90);
    }

    @Test
    void 같은_order가_둘이면_시작_실패() {
        assertThatThrownBy(
                        () ->
                                new WithdrawalStepRegistry(
                                        List.of(new Step(10), new Step(20), new Step(10))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("10");
    }

    @Test
    void 빠진_필수_단계만_돌려준다() {
        WithdrawalStepRegistry registry =
                new WithdrawalStepRegistry(List.of(new Step(10), new Step(45), new Step(90)));
        assertThat(registry.missingRequired(List.of(10, 20, 90))).containsExactly(20);
        assertThat(registry.missingRequired(List.of(10, 90))).isEmpty();
        assertThat(registry.missingRequired(List.of(65, 20, 80))).containsExactly(20, 65, 80);
    }

    @Test
    void 단계가_없어도_만들_수_있다() {
        WithdrawalStepRegistry registry = new WithdrawalStepRegistry(List.of());
        assertThat(registry.steps()).isEmpty();
        assertThat(registry.missingRequired(List.of(10))).containsExactly(10);
    }
}
