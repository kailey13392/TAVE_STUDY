package com.tave_2.shop.nplusone;

import com.tave_2.shop.domain.Order;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FetchJoinTest extends NPlusOneTestSupport {

    @Test
    void 페치조인으로_회원을_함께_조회하면_쿼리가_1번만_나간다() {
        List<Order> orders = orderRepository.findAllFetchMember();

        for (Order order : orders) {
            // 조인 시점에 이미 채워진 실제 엔티티 → 추가 SELECT 없음
            order.getMember().getName();
        }

        assertThat(executedQueryCount()).isEqualTo(1);
    }

    @Test
    void 페치조인으로_주문상품을_함께_조회하면_쿼리가_1번만_나간다() {
        List<Order> orders = orderRepository.findAllFetchOrderItems();

        for (Order order : orders) {
            order.getOrderItems().size();
        }

        assertThat(executedQueryCount()).isEqualTo(1);
        // orderItem 기준으로는 행이 늘어나지만(6주문 x 2개=12행), 주문은 중복 없이 6개
        assertThat(orders).hasSize(MEMBER_COUNT);
    }
}
