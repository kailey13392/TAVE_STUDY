package com.tave_2.shop.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders") // order는 예약어라서 테이블명을 바꿔야 한다
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue
    @Column(name = "order_id")
    private Long id;

    // N+1 포인트 ①: 주문 목록 조회 후 order.getMember()에 접근하는 순간 지연 로딩 발생
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    // N+1 포인트 ②: 주문 목록 조회 후 order.getOrderItems()에 접근하는 순간 지연 로딩 발생
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems = new ArrayList<>();

    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    public Order(Member member) {
        this.member = member;
        this.orderDate = LocalDateTime.now();
        this.status = OrderStatus.ORDERED;
        member.getOrders().add(this);
    }

    // 양방향 연관관계 편의 메서드: Order와 OrderItem 양쪽을 한 번에 세팅
    public void addOrderItem(OrderItem orderItem) {
        orderItems.add(orderItem);
        orderItem.assignOrder(this);
    }
}
