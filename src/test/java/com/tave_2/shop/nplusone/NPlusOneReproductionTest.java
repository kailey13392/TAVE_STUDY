package com.tave_2.shop.nplusone;

import com.tave_2.shop.domain.Order;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NPlusOneReproductionTest extends NPlusOneTestSupport {

    @Test
    void 주문목록_조회_후_회원에_접근하면_N번의_추가쿼리가_나간다() {
        List<Order> orders = orderRepository.findAll(); // 쿼리 1번

        for (Order order : orders) {
            // member는 LAZY 프록시 상태 → getName() 호출 시점에 SELECT 발생
            order.getMember().getName();
        }

        // 주문 조회 1번 + 회원 조회 N(6)번
        assertThat(executedQueryCount()).isEqualTo(1 + MEMBER_COUNT);
    }

    @Test
    void 주문목록_조회_후_주문상품에_접근하면_N번의_추가쿼리가_나간다() {
        List<Order> orders = orderRepository.findAll(); // 쿼리 1번

        for (Order order : orders) {
            // orderItems 컬렉션도 기본이 LAZY → size() 호출 시점에 주문마다 SELECT 발생
            order.getOrderItems().size();
        }

        // 주문 조회 1번 + 주문상품 조회 N(6)번
        assertThat(executedQueryCount()).isEqualTo(1 + MEMBER_COUNT);
    }
}
