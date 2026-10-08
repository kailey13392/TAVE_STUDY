package com.tave_2.cacheapi.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 생성 시간 / 수정 시간을 자동으로 채워 주는 공통 부모 엔티티.
 *
 * - @MappedSuperclass : 이 클래스는 테이블이 아니라, 자식 엔티티(Item 등)에 "컬럼만 물려준다"
 * - AuditingEntityListener : INSERT 직전 @CreatedDate, UPDATE 직전 @LastModifiedDate 를 채움
 *   (JpaAuditingConfig 의 @EnableJpaAuditing 이 켜져 있어야 동작)
 *
 * 엔티티가 늘어나도 extends BaseTimeEntity 한 줄이면 끝 → 도메인이 여러 개일 때 진가가 나온다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

	@CreatedDate
	@Column(updatable = false) // 생성 시간은 한 번 정해지면 안 바뀜
	private LocalDateTime createdAt;

	@LastModifiedDate
	private LocalDateTime updatedAt;
}
