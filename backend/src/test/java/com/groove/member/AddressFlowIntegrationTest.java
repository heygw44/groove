package com.groove.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.fixture.AddressFixture;
import com.groove.member.dto.AddressCreateRequest;
import com.groove.member.dto.AddressUpdateRequest;
import com.groove.member.entity.Address;
import com.groove.member.repository.AddressRepository;
import com.groove.member.service.AddressService;
import com.groove.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class AddressFlowIntegrationTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	AddressRepository addressRepository;

	@Nested
	@DisplayName("배송지 등록 → 조회 → 기본 지정 → 삭제 → 수정 흐름")
	class AddressCrudFlow {

		@Test
		@DisplayName("모든 변경 이후에도 기본 배송지는 정확히 1개로 유지된다")
		void keepsExactlyOneDefaultAddressThroughoutTheFlow() throws Exception {
			// given
			SignedUpMember signedUpMember = signupAndLogin();
			String accessToken = signedUpMember.accessToken();
			Long memberId = signedUpMember.memberId();

			// when: 배송지 3개를 등록한다(첫 배송지는 isDefault=false 여도 자동으로 기본이 된다)
			Long firstId = createAddress(accessToken, AddressFixture.createRequest(false));
			assertSingleDefault(memberId);

			Long secondId = createAddress(accessToken, AddressFixture.createRequest(false));
			assertSingleDefault(memberId);

			Long thirdId = createAddress(accessToken, AddressFixture.createRequest(false));
			assertSingleDefault(memberId);

			// then: 목록은 기본 배송지 우선 정렬이다
			mockMvc.perform(get("/api/v1/members/me/addresses")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].id", is(firstId.intValue())))
					.andExpect(jsonPath("$.data[0].isDefault", is(true)));

			// when: 두 번째 배송지를 기본으로 지정한다
			mockMvc.perform(patch("/api/v1/members/me/addresses/" + secondId + "/default")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.isDefault", is(true)));
			assertSingleDefault(memberId);
			assertThat(addressRepository.findById(secondId).orElseThrow().isDefault()).isTrue();

			// when: 기본 배송지(두 번째)를 삭제하면 남은 배송지 중 id 가 가장 작은 배송지가 기본이 된다
			mockMvc.perform(delete("/api/v1/members/me/addresses/" + secondId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk());
			assertSingleDefault(memberId);
			assertThat(addressRepository.findById(firstId).orElseThrow().isDefault()).isTrue();

			// when: 남은 배송지의 필드를 수정하면 반영된다
			AddressUpdateRequest updateRequest = new AddressUpdateRequest("수정된수령인", "010-1111-2222", "12345",
					"부산광역시 해운대구 센텀로 1", "3층");
			mockMvc.perform(patch("/api/v1/members/me/addresses/" + thirdId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(updateRequest)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.recipientName", is("수정된수령인")));
		}

		@Test
		@DisplayName("11번째 배송지를 등록하면 400 MEMBER_ADDRESS_LIMIT_EXCEEDED 를 반환한다")
		void returnsBadRequestWhenExceedingAddressLimit() throws Exception {
			// given
			String accessToken = signupAndLogin().accessToken();
			for (int i = 0; i < AddressService.MAX_ADDRESS_COUNT; i++) {
				createAddress(accessToken, AddressFixture.createRequest(false));
			}

			// when & then
			mockMvc.perform(post("/api/v1/members/me/addresses")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(AddressFixture.createRequest(false))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("MEMBER_ADDRESS_LIMIT_EXCEEDED")));
		}

		@Test
		@DisplayName("다른 회원의 배송지에 접근하면 404 MEMBER_ADDRESS_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenAccessingOtherMembersAddress() throws Exception {
			// given
			String ownerToken = signupAndLogin().accessToken();
			Long addressId = createAddress(ownerToken, AddressFixture.createRequest(true));
			String otherToken = signupAndLogin().accessToken();

			// when & then
			mockMvc.perform(patch("/api/v1/members/me/addresses/" + addressId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new AddressUpdateRequest("도용", "010-0000-0000", "00000", "가짜 주소", null))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("MEMBER_ADDRESS_NOT_FOUND")));

			mockMvc.perform(delete("/api/v1/members/me/addresses/" + addressId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("MEMBER_ADDRESS_NOT_FOUND")));

			mockMvc.perform(patch("/api/v1/members/me/addresses/" + addressId + "/default")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("MEMBER_ADDRESS_NOT_FOUND")));
		}
	}

	private void assertSingleDefault(Long memberId) {
		long defaultCount = addressRepository.findAllByMemberIdOrderByIdAsc(memberId).stream()
				.filter(Address::isDefault)
				.count();
		assertThat(defaultCount).isEqualTo(1);
	}

	private Long createAddress(String accessToken, AddressCreateRequest request) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/members/me/addresses")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.path("data").path("id").asLong();
	}

	private SignedUpMember signupAndLogin() throws Exception {
		String email = "addr-flow-" + UUID.randomUUID() + "@groove.com";
		String password = "password1";
		MvcResult signupResult = mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new SignupRequest(email, password, "그루버"))))
				.andExpect(status().isCreated())
				.andReturn();
		Long memberId = objectMapper.readTree(signupResult.getResponse().getContentAsString())
				.path("data").path("id").asLong();

		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
				.andExpect(status().isOk())
				.andReturn();
		String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
				.path("data").path("accessToken").asText();
		return new SignedUpMember(memberId, accessToken);
	}

	private record SignedUpMember(Long memberId, String accessToken) {
	}
}
