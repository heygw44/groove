package com.groove.catalog.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.SyncTaskExecutor;

import com.groove.catalog.client.PressingLookupClient;
import com.groove.catalog.client.dto.DiscogsMasterVersionsResponse.Version;
import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.dto.CatalogImportItem;
import com.groove.catalog.service.CatalogImportRegistrar;
import com.groove.catalog.support.FakePressingLookupClient;
import com.groove.fixture.DiscogsFixture;
import com.groove.fixture.GenreFixture;
import com.groove.inventory.repository.StockRepository;
import com.groove.product.entity.Album;
import com.groove.product.entity.EditionType;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductStatus;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * 별도 컨텍스트(@SpringBatchTest, @TestPropertySource)를 만들지 않고 기본 통합 컨텍스트를 공유한다.
 * 새 컨텍스트가 뜨면 공유 DB 테이블이 재생성돼 다른 통합 테스트의 id 가 초기화되기 때문이다.
 */
class DiscogsMasterImportJobIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private Job discogsMasterImportJob;

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private JobExplorer jobExplorer;

	@Autowired
	private JobOperator jobOperator;

	@Autowired
	private PressingLookupClient pressingLookupClient;

	@Autowired
	private CatalogImportRegistrar catalogImportRegistrar;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private GenreRepository genreRepository;

	private FakePressingLookupClient fake;
	private JobLauncherTestUtils jobLauncherTestUtils;

	@BeforeEach
	void setUp() throws Exception {
		new JobRepositoryTestUtils(jobRepository).removeJobExecutions();

		// 앱의 JobLauncher 는 비동기라 테스트에서는 동기 launcher 로 바꿔 끼운다.
		TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
		launcher.setJobRepository(jobRepository);
		launcher.setTaskExecutor(new SyncTaskExecutor());
		launcher.afterPropertiesSet();
		jobLauncherTestUtils = new JobLauncherTestUtils();
		jobLauncherTestUtils.setJob(discogsMasterImportJob);
		jobLauncherTestUtils.setJobRepository(jobRepository);
		jobLauncherTestUtils.setJobLauncher(launcher);

		fake = (FakePressingLookupClient) pressingLookupClient;
		fake.reset();

		if (!genreRepository.existsByName("Jazz")) {
			genreRepository.save(GenreFixture.create("Jazz"));
		}
	}

	@Nested
	@DisplayName("discogsMasterImportJob")
	class DiscogsMasterImportJobTest {

		@Test
		@DisplayName("바이닐 버전만 등록하고 이미 등록된 릴리즈는 숨김 상품으로 만들지 않는다")
		void completesAndCreatesHiddenPressings() throws Exception {
			// given
			long masterId = 9110001L;
			long releaseA = 9110001001L;
			long releaseB = 9110001002L;
			long releaseC = 9110001003L;
			long releaseCd = 9110001004L;
			long releaseDup = 9110001005L;

			catalogImportRegistrar.register(importItem(releaseDup, masterId, "Already Imported"));

			fake.addRelease(release(releaseA, masterId, "Album A", "Artist A"));
			fake.addRelease(release(releaseB, masterId, "Album B", "Artist B"));
			fake.addRelease(release(releaseC, masterId, "Album C", "Artist C"));
			fake.addMaster(masterId, List.of(
					List.of(vinylVersion(releaseA), vinylVersion(releaseB)),
					List.of(vinylVersion(releaseC), cdVersion(releaseCd), vinylVersion(releaseDup))));

			// when
			JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(masterId));

			// then
			assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
			StepExecution stepExecution = execution.getStepExecutions().iterator().next();
			assertThat(stepExecution.getReadCount()).isEqualTo(5);
			assertThat(stepExecution.getWriteCount()).isEqualTo(3);
			assertThat(stepExecution.getFilterCount()).isEqualTo(2);
			assertThat(stepExecution.getSkipCount()).isZero();

			List<Product> created = findProducts(Set.of(releaseA, releaseB, releaseC));
			assertThat(created).hasSize(3);
			assertThat(created).allSatisfy(product -> {
				assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
				assertThat(product.getDiscogsReleaseId()).isNotNull();
				assertThat(stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity()).isZero();
			});

			Album album = albumRepository.findByDiscogsMasterId(masterId).orElseThrow();
			assertThat(created).allSatisfy(product -> assertThat(product.getAlbum().getId()).isEqualTo(album.getId()));
		}

		@Test
		@DisplayName("릴리즈를 찾을 수 없으면 스킵하고 나머지는 정상 등록한다")
		void skipsMissingReleaseAndCompletes() throws Exception {
			// given
			long masterId = 9110002L;
			long missingRelease = 9110002001L;
			long releaseB = 9110002002L;
			long releaseC = 9110002003L;

			fake.markNotFound(missingRelease);
			fake.addRelease(release(releaseB, masterId, "Album B", "Artist B"));
			fake.addRelease(release(releaseC, masterId, "Album C", "Artist C"));
			fake.addMaster(masterId, List.of(
					List.of(vinylVersion(missingRelease), vinylVersion(releaseB), vinylVersion(releaseC))));

			// when
			JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(masterId));

			// then
			assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
			StepExecution stepExecution = execution.getStepExecutions().iterator().next();
			assertThat(stepExecution.getSkipCount()).isEqualTo(1);
			assertThat(findProducts(Set.of(releaseB, releaseC))).hasSize(2);

			String skipped = stepExecution.getExecutionContext()
					.getString(CatalogImportStepListener.SKIPPED_RELEASE_IDS_KEY);
			assertThat(skipped).contains(String.valueOf(missingRelease));
		}

		@Test
		@DisplayName("일시적 오류가 재시도 한도를 넘으면 실패하고 재시작하면 이어서 처리한다")
		void failsOnPersistentTransientErrorAndResumesOnRestart() throws Exception {
			// given
			long masterId = 9110003L;
			long releaseA = 9110003001L;
			long releaseB = 9110003002L;
			long releaseC = 9110003003L;
			long releaseD = 9110003004L;

			fake.addRelease(release(releaseA, masterId, "Album A", "Artist A"));
			fake.addRelease(release(releaseB, masterId, "Album B", "Artist B"));
			fake.addRelease(release(releaseC, masterId, "Album C", "Artist C"));
			fake.addRelease(release(releaseD, masterId, "Album D", "Artist D"));
			fake.addMaster(masterId, List.of(
					List.of(vinylVersion(releaseA)),
					List.of(vinylVersion(releaseB), vinylVersion(releaseC), vinylVersion(releaseD))));
			fake.failTransiently(releaseD, -1);

			JobParameters params = jobParameters(masterId);

			// when
			JobExecution failed = jobLauncherTestUtils.launchJob(params);

			// then
			assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
			assertThat(fake.masterPageCalls(masterId, 0)).isEqualTo(1);

			// when: 원인을 해소하고 같은 파라미터로 재시작한다
			fake.failTransiently(releaseD, 0);
			JobExecution resumed = jobLauncherTestUtils.launchJob(params);

			// then
			assertThat(resumed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
			assertThat(fake.masterPageCalls(masterId, 0)).isEqualTo(1);

			List<Product> created = findProducts(Set.of(releaseA, releaseB, releaseC, releaseD));
			assertThat(created).hasSize(4);

			long totalWrite = failed.getStepExecutions().iterator().next().getWriteCount()
					+ resumed.getStepExecutions().iterator().next().getWriteCount();
			assertThat(totalWrite).isEqualTo(4);
		}

		@Test
		@DisplayName("잡 오퍼레이터로 실패한 실행을 재시작하면 완료된다")
		void restartViaJobOperatorResumesFailedExecution() throws Exception {
			// given
			long masterId = 9110004L;
			long releaseA = 9110004001L;
			long releaseB = 9110004002L;

			fake.addRelease(release(releaseA, masterId, "Album A", "Artist A"));
			fake.addRelease(release(releaseB, masterId, "Album B", "Artist B"));
			fake.addMaster(masterId, List.of(
					List.of(vinylVersion(releaseA)),
					List.of(vinylVersion(releaseB))));
			fake.failTransiently(releaseB, -1);

			JobParameters params = jobParameters(masterId);
			JobExecution failed = jobLauncherTestUtils.launchJob(params);
			assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);

			fake.failTransiently(releaseB, 0);

			// when
			long newExecutionId = jobOperator.restart(failed.getId());
			waitUntilFinished(newExecutionId);

			// then
			JobExecution resumed = jobExplorer.getJobExecution(newExecutionId);
			assertThat(resumed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
			assertThat(findProducts(Set.of(releaseA, releaseB))).hasSize(2);
		}

		@Test
		@DisplayName("두 번째 실행은 이미 등록된 릴리즈를 전부 걸러낸다")
		void secondRunOnlyFiltersAlreadyImported() throws Exception {
			// given
			long masterId = 9110005L;
			long releaseA = 9110005001L;
			long releaseB = 9110005002L;

			fake.addRelease(release(releaseA, masterId, "Album A", "Artist A"));
			fake.addRelease(release(releaseB, masterId, "Album B", "Artist B"));
			fake.addMaster(masterId, List.of(List.of(vinylVersion(releaseA), vinylVersion(releaseB))));

			JobExecution first = jobLauncherTestUtils.launchJob(jobParameters(masterId));
			assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
			assertThat(findProducts(Set.of(releaseA, releaseB))).hasSize(2);

			// when
			JobExecution second = jobLauncherTestUtils.launchJob(jobParameters(masterId));

			// then
			assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
			StepExecution stepExecution = second.getStepExecutions().iterator().next();
			assertThat(stepExecution.getWriteCount()).isZero();
			assertThat(stepExecution.getFilterCount()).isEqualTo(2);
			assertThat(findProducts(Set.of(releaseA, releaseB))).hasSize(2);
		}

		@Test
		@DisplayName("일시적 오류는 재시도 후 성공한다")
		void retriesTransientFailureThenSucceeds() throws Exception {
			// given
			long masterId = 9110006L;
			long releaseA = 9110006001L;

			fake.addRelease(release(releaseA, masterId, "Album A", "Artist A"));
			fake.addMaster(masterId, List.of(List.of(vinylVersion(releaseA))));
			fake.failTransiently(releaseA, 2);

			// when
			JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters(masterId));

			// then
			assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
			assertThat(findProducts(Set.of(releaseA))).hasSize(1);
			assertThat(fake.releaseCalls(releaseA)).isEqualTo(3);
		}
	}

	private void waitUntilFinished(long executionId) throws InterruptedException {
		LocalDateTime deadline = LocalDateTime.now().plus(Duration.ofSeconds(20));
		while (LocalDateTime.now().isBefore(deadline)) {
			JobExecution execution = jobExplorer.getJobExecution(executionId);
			if (execution != null && !execution.isRunning()) {
				return;
			}
			Thread.sleep(200);
		}
	}

	private JobParameters jobParameters(long masterId) {
		return new JobParametersBuilder()
				.addLong(CatalogImportJobConfig.PARAM_MASTER_ID, masterId)
				.addString(CatalogImportJobConfig.PARAM_DEFAULT_PRICE, "45000", false)
				.addLocalDateTime(CatalogImportJobConfig.PARAM_REQUESTED_AT, LocalDateTime.now())
				.toJobParameters();
	}

	private Version vinylVersion(long releaseId) {
		return DiscogsFixture.version(releaseId, "Vinyl");
	}

	private Version cdVersion(long releaseId) {
		return DiscogsFixture.version(releaseId, "CD");
	}

	private DiscogsReleaseResponse release(long releaseId, long masterId, String title, String artistName) {
		DiscogsReleaseResponse base = DiscogsFixture.releaseResponse(artistName, "Columbia", "CS 8163",
				List.of("LP", "Album"), "5012394144777", List.of("Jazz"), List.of("Cool Jazz"));
		return new DiscogsReleaseResponse(releaseId, title, base.artists(), base.labels(), base.country(),
				base.year(), base.genres(), base.styles(), base.formats(), base.identifiers(), base.images(),
				masterId, base.notes());
	}

	private CatalogImportItem importItem(long releaseId, long masterId, String title) {
		return new CatalogImportItem(releaseId, masterId, title, "Already Artist", "Columbia", "US", 1959,
				"CS 8163", null, EditionType.STANDARD, List.of(), new BigDecimal("45000"));
	}

	private List<Product> findProducts(Set<Long> releaseIds) {
		return productRepository.findAll().stream()
				.filter(product -> product.getDiscogsReleaseId() != null
						&& releaseIds.contains(product.getDiscogsReleaseId()))
				.toList();
	}
}
