package com.groove.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ClientIpResolverTest {

	private final ClientIpResolver clientIpResolver = new ClientIpResolver();

	@AfterEach
	void tearDown() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Nested
	@DisplayName("resolve()")
	class Resolve {

		@Test
		@DisplayName("요청 스코프 밖에서 호출하면 null 을 반환한다")
		void returnsNullWhenNoRequest() {
			// when
			String ip = clientIpResolver.resolve();

			// then
			assertThat(ip).isNull();
		}

		@ParameterizedTest
		@ValueSource(strings = {
			"203.0.113.10, 198.51.100.7",
			"203.0.113.10,198.51.100.7",
			"203.0.113.10, 10.0.0.1, 198.51.100.7"
		})
		@DisplayName("요청자가 위조한 값이 앞에 섞여 있으면 프록시가 덧붙인 마지막 값을 반환한다")
		void returnsProxyAppendedLastValueWhenForwardedForIsForged(String forwardedFor) {
			// given
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.setRemoteAddr("127.0.0.1");
			request.addHeader("X-Forwarded-For", forwardedFor);
			RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

			// when
			String ip = clientIpResolver.resolve();

			// then
			assertThat(ip).isEqualTo("198.51.100.7");
		}

		@Test
		@DisplayName("X-Forwarded-For 의 마지막 값이 비어 있으면 위조된 앞 값이 아닌 원격 주소를 반환한다")
		void returnsRemoteAddrWhenLastForwardedForValueIsEmpty() {
			// given
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.setRemoteAddr("127.0.0.1");
			request.addHeader("X-Forwarded-For", "203.0.113.10, ");
			RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

			// when
			String ip = clientIpResolver.resolve();

			// then
			assertThat(ip).isEqualTo("127.0.0.1");
		}

		@Test
		@DisplayName("X-Forwarded-For 가 없으면 원격 주소를 반환한다")
		void returnsRemoteAddrWhenNoForwardedForHeader() {
			// given
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.setRemoteAddr("192.168.0.1");
			RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

			// when
			String ip = clientIpResolver.resolve();

			// then
			assertThat(ip).isEqualTo("192.168.0.1");
		}

		@Test
		@DisplayName("X-Forwarded-For 가 빈 값이면 원격 주소를 반환한다")
		void returnsRemoteAddrWhenForwardedForHeaderIsBlank() {
			// given
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.addHeader("X-Forwarded-For", "   ");
			request.setRemoteAddr("192.168.0.1");
			RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

			// when
			String ip = clientIpResolver.resolve();

			// then
			assertThat(ip).isEqualTo("192.168.0.1");
		}

		@Test
		@DisplayName("X-Forwarded-For 도 원격 주소도 없으면 null 을 반환한다")
		void returnsNullWhenNeitherForwardedForNorRemoteAddrPresent() {
			// given
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.setRemoteAddr(null);
			RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

			// when
			String ip = clientIpResolver.resolve();

			// then
			assertThat(ip).isNull();
		}

		@Test
		@DisplayName("주소가 45자를 넘으면 45자로 잘라 반환한다")
		void truncatesIpWhenLongerThanMaxLength() {
			// given
			String longIp = "1".repeat(50);
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.addHeader("X-Forwarded-For", longIp);
			RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

			// when
			String ip = clientIpResolver.resolve();

			// then
			assertThat(ip).hasSize(45);
			assertThat(ip).isEqualTo(longIp.substring(0, 45));
		}
	}
}
