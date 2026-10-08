package com.tave_2.cacheapi.global.common;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 페이징 응답 DTO.
 *
 * 왜 스프링의 Page 를 그대로 반환하지 않나?
 *  1) Page(PageImpl)를 JSON 으로 그대로 내보내면 pageable, sort 같은 내부 구조가 다 노출되고,
 *     스프링 데이터도 "형식이 안정적이지 않으니 DTO 로 바꿔 쓰라"고 경고 로그를 띄운다.
 *  2) 캐시(Redis)에 넣으려면 Serializable 이어야 하는데, 내가 만든 DTO 가 관리하기 쉽다.
 *
 * 응답 예: { "content": [...], "page": 0, "size": 10, "totalElements": 23, "totalPages": 3, "hasNext": true }
 */
public record PageResponse<T>(
		List<T> content,
		int page,              // 현재 페이지 번호 (0부터 시작)
		int size,              // 한 페이지 크기
		long totalElements,    // 전체 데이터 수
		int totalPages,        // 전체 페이지 수
		boolean hasNext        // 다음 페이지가 있는지 (무한 스크롤에서 유용)
) implements Serializable {

	/** Page<엔티티> → PageResponse<DTO> 변환. mapper 로 엔티티를 DTO 로 바꾼다 */
	public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(
				page.getContent().stream().map(mapper).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.hasNext()
		);
	}
}
