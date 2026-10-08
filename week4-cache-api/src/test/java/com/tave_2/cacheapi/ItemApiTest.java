package com.tave_2.cacheapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 5개 + 공통 응답 형식 + 에러 응답 + Actuator 헬스체크를 HTTP 요청 수준에서 확인하는 테스트.
 * MockMvc = 실제 서버를 띄우지 않고 컨트롤러에 가짜 HTTP 요청을 보내 보는 도구.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ItemApiTest {

	@Autowired MockMvc mockMvc;

	@Test
	void 상품_CRUD_흐름과_공통응답형식() throws Exception {
		// 등록 → 201 + Location 헤더, { success: true, data: {...} }
		String body = mockMvc.perform(post("/api/v1/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"태블릿\",\"price\":500000,\"stockQuantity\":4}"))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.createdAt").exists())
				.andExpect(jsonPath("$.error").doesNotExist()) // 성공이면 error 필드 자체가 없음 (NON_NULL)
				.andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

		// 단건 조회
		mockMvc.perform(get("/api/v1/items/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("태블릿"));

		// 목록(페이징) 조회
		mockMvc.perform(get("/api/v1/items").param("page", "0").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").isArray())
				.andExpect(jsonPath("$.data.size").value(2))
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.totalElements").exists())
				.andExpect(jsonPath("$.data.hasNext").exists());

		// 수정
		mockMvc.perform(put("/api/v1/items/" + id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"태블릿 Pro\",\"price\":700000,\"stockQuantity\":4}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("태블릿 Pro"));

		// 삭제 → 204, 이후 조회 → 404 { success: false, error: { code: ITEM_NOT_FOUND } }
		mockMvc.perform(delete("/api/v1/items/" + id)).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/items/" + id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));
	}

	@Test
	void 요청본문_검증_실패하면_400() throws Exception {
		mockMvc.perform(post("/api/v1/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"\",\"price\":0,\"stockQuantity\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.error.message").value("상품 이름은 필수입니다."));
	}

	@Test
	void 페이지_크기가_범위를_넘으면_400() throws Exception {
		mockMvc.perform(get("/api/v1/items").param("size", "1000"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.error.message").value("size 는 50 이하여야 합니다."));
		mockMvc.perform(get("/api/v1/items").param("page", "-1"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void id_타입이_틀리면_400() throws Exception {
		mockMvc.perform(get("/api/v1/items/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_TYPE"));
	}

	@Test
	void JSON_문법이_틀리면_400() throws Exception {
		mockMvc.perform(post("/api/v1/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_JSON"));
	}

	@Test
	void 지원하지_않는_메서드면_405() throws Exception {
		mockMvc.perform(patch("/api/v1/items/1"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	void 액추에이터_헬스체크는_UP() throws Exception {
		// local 은 show-details: always 라서 components(db 등)까지 보인다.
		// Actuator 응답은 우리 ApiResponse 로 감싸지 않는다 (로드밸런서 등이 표준 형식을 기대하므로)
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components.db.status").value("UP"));
	}
}
