package com.groove.member.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.jwt.JwtProvider;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.dto.AddressCreateRequest;
import com.groove.member.dto.AddressResponse;
import com.groove.member.dto.AddressUpdateRequest;
import com.groove.member.entity.MemberRole;
import com.groove.member.service.AddressService;

@WebMvcTest(AddressController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class AddressControllerTest {

	private static final String BASE_URL = "/api/v1/members/me/addresses";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	AddressService addressService;

	private String bearer() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	private AddressResponse sampleResponse() {
		return new AddressResponse(10L, "김그루브", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "101동 1001호", true);
	}

	private AddressCreateRequest createRequest() {
		return new AddressCreateRequest("김그루브", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "101동 1001호", true);
	}

	private AddressUpdateRequest updateRequest() {
		return new AddressUpdateRequest("김그루브", "010-1234-5678", "06236", "서울시 강남구 테헤란로 1", "101동 1001호");
	}

	@Nested
	@DisplayName("GET /api/v1/members/me/addresses")
	class GetAddresses {

		@Test
		@DisplayName("인증된 요청이면 200 과 배송지 목록을 반환한다")
		void returnsAddresses() throws Exception {
			// given
			given(addressService.getAddresses(1L)).willReturn(List.of(sampleResponse()));

			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].id", is(10)))
					.andExpect(jsonPath("$.data[0].isDefault", is(true)));
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(addressService, never()).getAddresses(any());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/members/me/addresses")
	class Create {

		@Test
		@DisplayName("유효한 요청이면 201 과 생성된 배송지를 반환한다")
		void createsAddress() throws Exception {
			// given
			given(addressService.create(eq(1L), any())).willReturn(sampleResponse());

			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest())))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.id", is(10)));
			verify(addressService).create(eq(1L), any());
		}

		@ParameterizedTest
		@DisplayName("전화번호 형식이 올바르지 않으면 400 과 필드 에러를 반환한다")
		@CsvSource({"01012345678", "010-12-4567", "010-12345-6789", "''"})
		void returnsBadRequestWhenPhoneInvalid(String phone) throws Exception {
			// given
			AddressCreateRequest request = new AddressCreateRequest("김그루브", phone, "06236", "서울시 강남구 테헤란로 1",
					"101동 1001호", true);

			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("phone")));
		}

		@ParameterizedTest
		@DisplayName("우편번호 형식이 올바르지 않으면 400 과 필드 에러를 반환한다")
		@CsvSource({"6236", "062366", "abcde", "''"})
		void returnsBadRequestWhenZipCodeInvalid(String zipCode) throws Exception {
			// given
			AddressCreateRequest request = new AddressCreateRequest("김그루브", "010-1234-5678", zipCode,
					"서울시 강남구 테헤란로 1", "101동 1001호", true);

			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("zipCode")));
		}

		@Test
		@DisplayName("수령인이 비어 있으면 400 과 필드 에러를 반환한다")
		void returnsBadRequestWhenRecipientNameBlank() throws Exception {
			// given
			AddressCreateRequest request = new AddressCreateRequest("", "010-1234-5678", "06236",
					"서울시 강남구 테헤란로 1", "101동 1001호", true);

			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("recipientName")));
		}

		@Test
		@DisplayName("등록 한도를 초과하면 400 MEMBER_ADDRESS_LIMIT_EXCEEDED 를 반환한다")
		void returnsBadRequestWhenLimitExceeded() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.MEMBER_ADDRESS_LIMIT_EXCEEDED))
					.given(addressService).create(eq(1L), any());

			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest())))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("MEMBER_ADDRESS_LIMIT_EXCEEDED")));
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/members/me/addresses/{addressId}")
	class Update {

		@Test
		@DisplayName("유효한 요청이면 200 과 수정된 배송지를 반환한다")
		void updatesAddress() throws Exception {
			// given
			given(addressService.update(eq(1L), eq(10L), any())).willReturn(sampleResponse());

			// when & then
			mockMvc.perform(patch(BASE_URL + "/10")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(updateRequest())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id", is(10)));
		}

		@Test
		@DisplayName("본인 소유가 아니면 404 MEMBER_ADDRESS_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenNotOwned() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.MEMBER_ADDRESS_NOT_FOUND))
					.given(addressService).update(eq(1L), eq(10L), any());

			// when & then
			mockMvc.perform(patch(BASE_URL + "/10")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(updateRequest())))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("MEMBER_ADDRESS_NOT_FOUND")));
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/members/me/addresses/{addressId}")
	class Delete {

		@Test
		@DisplayName("인증된 요청이면 200 을 반환하고 삭제를 처리한다")
		void deletesAddress() throws Exception {
			// when & then
			mockMvc.perform(delete(BASE_URL + "/10").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk());
			verify(addressService).delete(1L, 10L);
		}

		@Test
		@DisplayName("존재하지 않는 id 면 404 MEMBER_ADDRESS_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenAddressMissing() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.MEMBER_ADDRESS_NOT_FOUND))
					.given(addressService).delete(1L, 10L);

			// when & then
			mockMvc.perform(delete(BASE_URL + "/10").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("MEMBER_ADDRESS_NOT_FOUND")));
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(delete(BASE_URL + "/10"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(addressService, never()).delete(any(), any());
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/members/me/addresses/{addressId}/default")
	class SetDefault {

		@Test
		@DisplayName("인증된 요청이면 200 과 변경된 배송지를 반환한다")
		void setsDefaultAddress() throws Exception {
			// given
			given(addressService.setDefault(1L, 10L)).willReturn(sampleResponse());

			// when & then
			mockMvc.perform(patch(BASE_URL + "/10/default").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.isDefault", is(true)));
		}

		@Test
		@DisplayName("본인 소유가 아니면 404 MEMBER_ADDRESS_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenNotOwned() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.MEMBER_ADDRESS_NOT_FOUND))
					.given(addressService).setDefault(1L, 10L);

			// when & then
			mockMvc.perform(patch(BASE_URL + "/10/default").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("MEMBER_ADDRESS_NOT_FOUND")));
		}
	}
}
