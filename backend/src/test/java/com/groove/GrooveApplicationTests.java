package com.groove;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.groove.support.IntegrationTestSupport;

import io.micrometer.registry.otlp.OtlpMeterRegistry;

class GrooveApplicationTests extends IntegrationTestSupport {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
	}

	@Test
	void otlpMeterRegistryIsNotRegisteredWhenExportIsDisabled() {
		// when & then: export 를 켜지 않는 한 OtlpMeterRegistry 빈이 만들어지면 안 된다.
		assertThat(applicationContext.getBeanNamesForType(OtlpMeterRegistry.class)).isEmpty();
	}
}
