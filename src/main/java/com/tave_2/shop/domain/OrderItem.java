package com.tave_2.shop.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue
    @Column(name = "order_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    // N+1 포인트 ③: orderItem.getItem()에 접근하는 순간 지연 로딩 발생
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    private int orderPrice;
    private int count;

    public OrderItem(Item item, int count) {
        this.item = item;
        this.orderPrice = item.getPrice();
        this.count = count;
    }

    // Order.addOrderItem()을 통해서만 세팅되도록 패키지 접근으로 제한
    void assignOrder(Order order) {
        this.order = order;
    }
}
