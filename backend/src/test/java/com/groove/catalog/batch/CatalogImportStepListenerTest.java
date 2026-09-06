package com.groove.catalog.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.MetaDataInstanceFactory;

import com.groove.catalog.dto.CatalogImportItem;
import com.groove.fixture.DiscogsFixture;
import com.groove.product.entity.EditionType;

class CatalogImportStepListenerTest {

	private final CatalogImportStepListener listener = new CatalogImportStepListener();

	@Nested
	@DisplayName("afterStep()")
	class AfterStep {

		@Test
		@DisplayName("스킵된 릴리즈 id 를 콤마로 이어 ExecutionContext 에 저장한다")
		void joinsSkippedReleaseIdsIntoExecutionContext() {
			// given
			StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
			listener.beforeStep(stepExecution);
			listener.onSkipInProcess(DiscogsFixture.version(1L, "Album"), new RuntimeException("실패"));
			CatalogImportItem item = new CatalogImportItem(2L, 21247L, "Kind Of Blue", "Miles Davis", "Columbia",
					"US", 1959, "CS 8163", "123", EditionType.STANDARD, List.of("Jazz"), BigDecimal.valueOf(30000));
			listener.onSkipInWrite(item, new RuntimeException("실패"));

			// when
			listener.afterStep(stepExecution);

			// then
			assertThat(stepExecution.getExecutionContext().getString(CatalogImportStepListener.SKIPPED_RELEASE_IDS_KEY))
					.isEqualTo("1,2");
		}

		@Test
		@DisplayName("스킵된 항목이 없으면 빈 문자열을 저장한다")
		void storesEmptyStringWhenNothingSkipped() {
			// given
			StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
			listener.beforeStep(stepExecution);

			// when
			listener.afterStep(stepExecution);

			// then
			assertThat(stepExecution.getExecutionContext().getString(CatalogImportStepListener.SKIPPED_RELEASE_IDS_KEY))
					.isEmpty();
		}

		@Test
		@DisplayName("Step 의 ExitStatus 를 그대로 반환한다")
		void returnsStepExitStatus() {
			// given
			StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
			listener.beforeStep(stepExecution);
			stepExecution.setExitStatus(ExitStatus.FAILED);

			// when
			ExitStatus result = listener.afterStep(stepExecution);

			// then
			assertThat(result).isEqualTo(ExitStatus.FAILED);
		}
	}
}
