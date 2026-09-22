package com.tave_2.shop.nplusone;

import com.tave_2.shop.domain.Order;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * default_batch_fetch_size는 이 테스트에서만 켠다.
 * application.yaml에서 전역으로 켜두면 NPlusOneReproductionTest의 N+1 재현이 깨지기 때문.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_batch_fetch_size=100")
class BatchSizeTest extends NPlusOneTestSupport {

    @Test
    void 배치사이즈를_설정하면_회원을_한번에_묶어서_조회한다() {
        List<Order> orders = orderRepository.findAll(); // N+1 재현 테스트와 동일한 쿼리

        for (Order order : orders) {
            // 첫 프록시 초기화 시점에, 같은 배치에 속한 나머지 회원까지 IN절로 한 번에 조회
            order.getMember().getName();
        }

        // 주문 조회 1번 + 회원 묶음 조회 1번
        assertThat(executedQueryCount()).isEqualTo(2);
    }

    @Test
    void 배치사이즈를_설정하면_주문상품을_한번에_묶어서_조회한다() {
        List<Order> orders = orderRepository.findAll();

        for (Order order : orders) {
            order.getOrderItems().size();
        }

        // 주문 조회 1번 + 주문상품 묶음 조회 1번
        assertThat(executedQueryCount()).isEqualTo(2);
    }
}
