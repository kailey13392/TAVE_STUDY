package com.tave_2.cacheapi.controller;

import com.tave_2.cacheapi.dto.ItemCreateRequest;
import com.tave_2.cacheapi.dto.ItemResponse;
import com.tave_2.cacheapi.dto.ItemUpdateRequest;
import com.tave_2.cacheapi.service.ItemService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 API (5개). 컨트롤러는 요청/응답 변환만 하고, 캐시 로직은 전부 ItemService 에 있다.
 *
 *  POST   /api/items        상품 등록   201
 *  GET    /api/items        목록 조회   200  (캐시)
 *  GET    /api/items/{id}   단건 조회   200  (캐시)
 *  PUT    /api/items/{id}   상품 수정   200  (캐시 갱신)
 *  DELETE /api/items/{id}   상품 삭제   204  (캐시 삭제)
 */
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

	private final ItemService itemService;

	@PostMapping
	public ResponseEntity<ItemResponse> create(@Valid @RequestBody ItemCreateRequest request) {
		ItemResponse created = itemService.createItem(request);
		// 201 Created + Location 헤더에 새로 만든 상품 주소를 담아 준다 (REST 관례)
		return ResponseEntity.created(URI.create("/api/items/" + created.id())).body(created);
	}

	@GetMapping
	public List<ItemResponse> list() {
		return itemService.getItems();
	}

	@GetMapping("/{id}")
	public ItemResponse get(@PathVariable Long id) {
		return itemService.getItem(id);
	}

	@PutMapping("/{id}")
	public ItemResponse update(@PathVariable Long id, @Valid @RequestBody ItemUpdateRequest request) {
		return itemService.updateItem(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		itemService.deleteItem(id);
		return ResponseEntity.noContent().build();
	}
}
