package com.tave_2.shop.nplusone;

import com.tave_2.shop.domain.Order;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntityGraphTest extends NPlusOneTestSupport {

    @Test
    void 엔티티그래프로_회원을_함께_조회하면_쿼리가_1번만_나간다() {
        List<Order> orders = orderRepository.findAllWithMemberGraph();

        for (Order order : orders) {
            order.getMember().getName();
        }

        assertThat(executedQueryCount()).isEqualTo(1);
    }

    @Test
    void 엔티티그래프로_주문상품을_함께_조회하면_쿼리가_1번만_나간다() {
        List<Order> orders = orderRepository.findAllWithOrderItemsGraph();

        for (Order order : orders) {
            order.getOrderItems().size();
        }

        assertThat(executedQueryCount()).isEqualTo(1);
        assertThat(orders).hasSize(MEMBER_COUNT);
    }
}
