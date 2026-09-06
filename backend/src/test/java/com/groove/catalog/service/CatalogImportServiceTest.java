package com.groove.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.catalog.client.PressingLookupClient;
import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.dto.CatalogImportRequest;
import com.groove.catalog.dto.CatalogImportResponse;
import com.groove.catalog.dto.CatalogImportResult;
import com.groove.fixture.DiscogsFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class CatalogImportServiceTest {

	@Mock
	PressingLookupClient client;

	@Mock
	ProductRepository productRepository;

	@Mock
	GenreRepository genreRepository;

	@Mock
	CatalogImportRegistrar registrar;

	@Mock
	AdminAuditLogService adminAuditLogService;

	@Mock
	TransactionTemplate transactionTemplate;

	final DiscogsReleaseMapper mapper = new DiscogsReleaseMapper();

	CatalogImportService service() {
		return new CatalogImportService(client, productRepository, genreRepository, mapper, registrar,
				adminAuditLogService, transactionTemplate);
	}

	@Nested
	@DisplayName("importRelease()")
	class ImportRelease {

		@Test
		@DisplayName("이미 등록된 릴리즈면 Discogs 를 호출하지 않고 CATALOG_ALREADY_IMPORTED 예외를 던진다")
		void throwsWhenAlreadyImported() {
			// given
			given(productRepository.existsByDiscogsReleaseId(249504L)).willReturn(true);
			CatalogImportRequest request = new CatalogImportRequest(249504L, BigDecimal.valueOf(45000));

			// when & then
			assertThatThrownBy(() -> service().importRelease(1L, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CATALOG_ALREADY_IMPORTED);
			verify(client, never()).getRelease(anyLong());
		}

		@Test
		@DisplayName("Vinyl 포맷이 아니면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenNotVinyl() {
			// given
			given(productRepository.existsByDiscogsReleaseId(249504L)).willReturn(false);
			DiscogsReleaseResponse release = new DiscogsReleaseResponse(249504L, "Kind Of Blue",
					List.of(new DiscogsReleaseResponse.Artist("Miles Davis")),
					List.of(new DiscogsReleaseResponse.Label("Columbia", "CS 8163")), "US", 1959, List.of("Jazz"),
					List.of(), List.of(new DiscogsReleaseResponse.Format("CD", List.of())), List.of(), List.of(),
					21247L, "Discogs 원본 노트");
			given(client.getRelease(249504L)).willReturn(release);
			CatalogImportRequest request = new CatalogImportRequest(249504L, BigDecimal.valueOf(45000));

			// when & then
			assertThatThrownBy(() -> service().importRelease(1L, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("등록에 성공하면 상품/앨범 id 를 반환하고 감사 로그를 기록한다")
		void registersAndRecordsAudit() {
			// given
			given(productRepository.existsByDiscogsReleaseId(249504L)).willReturn(false);
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse("Miles Davis", "Columbia", "CS 8163",
					List.of("LP"), "5012394144777", List.of("Jazz"), List.of());
			given(client.getRelease(249504L)).willReturn(release);
			given(genreRepository.findAllByOrderByNameAsc()).willReturn(List.of());
			given(registrar.register(any())).willReturn(new CatalogImportResult(733L, 310L));
			given(transactionTemplate.execute(any())).willAnswer(invocation -> {
				TransactionCallback<CatalogImportResult> callback = invocation.getArgument(0);
				return callback.doInTransaction(null);
			});
			CatalogImportRequest request = new CatalogImportRequest(249504L, BigDecimal.valueOf(45000));

			// when
			CatalogImportResponse response = service().importRelease(1L, request);

			// then
			assertThat(response.productId()).isEqualTo(733L);
			assertThat(response.albumId()).isEqualTo(310L);
			verify(client, times(1)).getRelease(249504L);
			verify(adminAuditLogService).record(1L, AdminAuditAction.PRODUCT_IMPORT, AdminAuditTargetType.PRODUCT,
					733L, "discogsReleaseId=249504");
		}
	}
}
