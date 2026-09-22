package com.tave_2.shop.repository;

import com.tave_2.shop.domain.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // 해결 1: 페치 조인 — JPQL에 join fetch를 직접 명시
    @Query("select o from Order o join fetch o.member")
    List<Order> findAllFetchMember();

    // Hibernate 6부터 컬렉션 페치 조인 결과의 중복 엔티티를 자동 제거해주므로 distinct 불필요
    @Query("select o from Order o join fetch o.orderItems")
    List<Order> findAllFetchOrderItems();

    // 해결 2: @EntityGraph — JPQL은 그대로 두고 함께 로딩할 연관관계만 어노테이션으로 지정
    @EntityGraph(attributePaths = "member")
    @Query("select o from Order o")
    List<Order> findAllWithMemberGraph();

    @EntityGraph(attributePaths = "orderItems")
    @Query("select o from Order o")
    List<Order> findAllWithOrderItemsGraph();

    // 해결 3: default_batch_fetch_size — 쿼리는 findAll() 그대로, 설정만으로 해결
}
