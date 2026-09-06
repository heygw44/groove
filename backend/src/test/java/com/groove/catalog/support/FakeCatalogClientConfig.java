package com.groove.catalog.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.groove.catalog.client.PressingLookupClient;

/**
 * 통합 테스트 컨텍스트에서 Discogs 호출을 인메모리 Fake 로 대체한다.
 * 테스트 클래스마다 @Import 로 컨텍스트를 새로 만들지 않도록 test 프로파일 전역에 등록한다.
 */
@Profile("test")
@Configuration
public class FakeCatalogClientConfig {

	@Bean
	@Primary
	public PressingLookupClient fakePressingLookupClient() {
		return new FakePressingLookupClient();
	}
}
