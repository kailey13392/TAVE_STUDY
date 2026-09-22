package com.tave_2.shop.nplusone;

import com.tave_2.shop.domain.Item;
import com.tave_2.shop.domain.Member;
import com.tave_2.shop.domain.Order;
import com.tave_2.shop.domain.OrderItem;
import com.tave_2.shop.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * N+1 재현/해결 테스트에서 공통으로 쓰는 데이터.
 * 회원 6명이 각자 주문을 1건씩 하고, 주문마다 서로 다른 상품 2종류를 담는다.
 * (회원이 겹치면 1차 캐시에서 꺼내 와서 N+1이 잘 드러나지 않기 때문에, 회원 수만큼 주문을 나눠 만든다.)
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
abstract class NPlusOneTestSupport {

    static final int MEMBER_COUNT = 6;

    @Autowired
    EntityManager em;
    @Autowired
    OrderRepository orderRepository;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        Item keyboard = new Item("키보드", 55000, 50);
        Item mouse = new Item("마우스", 25000, 80);

        em.persist(keyboard);
        em.persist(mouse);

        for (int i = 1; i <= MEMBER_COUNT; i++) {
            Member member = new Member("회원" + i);
            em.persist(member);

            Order order = new Order(member);
            order.addOrderItem(new OrderItem(keyboard, 1));
            order.addOrderItem(new OrderItem(mouse, 2));
            em.persist(order); // cascade = ALL 이라서 OrderItem은 따로 persist하지 않아도 된다
        }

        // 영속성 컨텍스트(1차 캐시)에 남아 있으면 이후 조회가 캐시에서 바로 나가버려
        // N+1을 재현할 수 없다. flush로 DB에 반영하고 clear로 1차 캐시를 비운다.
        em.flush();
        em.clear();

        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    long executedQueryCount() {
        return statistics.getPrepareStatementCount();
    }
}
