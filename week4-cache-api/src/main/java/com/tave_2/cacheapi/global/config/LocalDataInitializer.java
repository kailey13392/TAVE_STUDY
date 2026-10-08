package com.tave_2.cacheapi.global.config;

import com.tave_2.cacheapi.domain.item.entity.Item;
import com.tave_2.cacheapi.domain.item.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * local 에서 서버를 켜자마자 바로 API 를 눌러볼 수 있게 샘플 상품을 넣어 둔다.
 *
 * @Profile("local") → local 프로파일일 때만 이 빈이 만들어진다.
 *   prod 에서는 아예 빈이 안 생기므로 실제 운영 DB 에 테스트 데이터가 들어갈 일이 없다.
 */
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

	private final ItemRepository itemRepository;

	@Override
	public void run(ApplicationArguments args) {
		itemRepository.save(new Item("무선 키보드", 59000, 30));
		itemRepository.save(new Item("기계식 마우스", 39000, 50));
		itemRepository.save(new Item("27인치 모니터", 289000, 10));
	}
}
