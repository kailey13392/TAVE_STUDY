package com.tave_2.cacheapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * API 5개 + Actuator 헬스체크를 HTTP 요청 수준에서 확인하는 테스트.
 * MockMvc = 실제 서버를 띄우지 않고 컨트롤러에 가짜 HTTP 요청을 보내 보는 도구.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ItemApiTest {

	@Autowired MockMvc mockMvc;

	@Test
	void 상품_CRUD_흐름() throws Exception {
		// 등록 → 201 + Location 헤더
		String body = mockMvc.perform(post("/api/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"태블릿\",\"price\":500000,\"stockQuantity\":4}"))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

		// 단건 조회
		mockMvc.perform(get("/api/items/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("태블릿"));

		// 목록 조회
		mockMvc.perform(get("/api/items"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray());

		// 수정
		mockMvc.perform(put("/api/items/" + id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"태블릿 Pro\",\"price\":700000,\"stockQuantity\":4}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("태블릿 Pro"));

		// 삭제 → 204, 이후 조회 → 404
		mockMvc.perform(delete("/api/items/" + id)).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/items/" + id))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
	}

	@Test
	void 검증_실패하면_400() throws Exception {
		mockMvc.perform(post("/api/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"\",\"price\":-1,\"stockQuantity\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void 액추에이터_헬스체크는_UP() throws Exception {
		// local 은 show-details: always 라서 components(db 등)까지 보인다
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components.db.status").value("UP"));
	}
}
