package com.groove.catalog.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.context.ApplicationEventPublisher;

import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.notification.service.NewPressingEvent;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.repository.AlbumRepository;
import com.groove.recommend.service.ProductCatalogChangedEvent;

@ExtendWith(MockitoExtension.class)
class CatalogImportJobListenerTest {

	@Mock
	private AlbumRepository albumRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private CatalogImportJobListener listener;

	@Captor
	private ArgumentCaptor<NewPressingEvent> newPressingEventCaptor;

	private JobExecution jobExecution(long masterId) {
		JobInstance instance = new JobInstance(1L, CatalogImportJobConfig.JOB_NAME);
		return new JobExecution(instance, 88L, new JobParametersBuilder()
				.addLong(CatalogImportJobConfig.PARAM_MASTER_ID, masterId)
				.toJobParameters());
	}

	@Nested
	@DisplayName("afterJob()")
	class AfterJob {

		@Test
		@DisplayName("적재된 프레싱이 있으면 앨범당 한 건의 NewPressingEvent 를 발행한다")
		void publishesNewPressingWhenAnyWritten() {
			// given
			JobExecution execution = jobExecution(21247L);
			StepExecution step = execution.createStepExecution("step1");
			step.setWriteCount(3);
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Album album = AlbumFixture.withId(AlbumFixture.create(artist), 3L);
			given(albumRepository.findByDiscogsMasterId(21247L)).willReturn(Optional.of(album));

			// when
			listener.afterJob(execution);

			// then
			verify(eventPublisher).publishEvent(newPressingEventCaptor.capture());
			assertThat(newPressingEventCaptor.getValue().albumId()).isEqualTo(3L);
			assertThat(newPressingEventCaptor.getValue().albumTitle()).isEqualTo(album.getTitle());
		}

		@Test
		@DisplayName("적재된 프레싱이 있으면 추천 특성 캐시 무효화 이벤트도 발행한다")
		void publishesCatalogChangedWhenAnyWritten() {
			// given
			JobExecution execution = jobExecution(21247L);
			StepExecution step = execution.createStepExecution("step1");
			step.setWriteCount(3);
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Album album = AlbumFixture.withId(AlbumFixture.create(artist), 3L);
			given(albumRepository.findByDiscogsMasterId(21247L)).willReturn(Optional.of(album));

			// when
			listener.afterJob(execution);

			// then
			verify(eventPublisher).publishEvent(any(ProductCatalogChangedEvent.class));
		}

		@Test
		@DisplayName("적재된 프레싱이 없으면 발행하지 않는다")
		void skipsWhenNothingWritten() {
			// given
			JobExecution execution = jobExecution(21247L);
			StepExecution step = execution.createStepExecution("step1");
			step.setWriteCount(0);

			// when
			listener.afterJob(execution);

			// then
			verify(albumRepository, never()).findByDiscogsMasterId(any());
			verify(eventPublisher, never()).publishEvent(any(NewPressingEvent.class));
			verify(eventPublisher, never()).publishEvent(any(ProductCatalogChangedEvent.class));
		}

		@Test
		@DisplayName("masterId 로 앨범을 찾지 못하면 발행하지 않는다")
		void skipsWhenAlbumNotFound() {
			// given
			JobExecution execution = jobExecution(21247L);
			StepExecution step = execution.createStepExecution("step1");
			step.setWriteCount(2);
			given(albumRepository.findByDiscogsMasterId(21247L)).willReturn(Optional.empty());

			// when
			listener.afterJob(execution);

			// then
			verify(eventPublisher, never()).publishEvent(any(NewPressingEvent.class));
		}

		@Test
		@DisplayName("스텝이 여러 개여도 잡 전체에 한 건만 발행한다")
		void publishesOnceEvenWithMultipleSteps() {
			// given
			JobExecution execution = jobExecution(21247L);
			StepExecution step1 = execution.createStepExecution("step1");
			step1.setWriteCount(2);
			StepExecution step2 = execution.createStepExecution("step2");
			step2.setWriteCount(1);
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Album album = AlbumFixture.withId(AlbumFixture.create(artist), 3L);
			given(albumRepository.findByDiscogsMasterId(21247L)).willReturn(Optional.of(album));

			// when
			listener.afterJob(execution);

			// then
			verify(eventPublisher).publishEvent(any(NewPressingEvent.class));
		}

		@Test
		@DisplayName("잡이 FAILED 로 끝나도 적재분이 있으면 발행한다")
		void publishesEvenWhenJobFailed() {
			// given
			JobExecution execution = jobExecution(21247L);
			execution.setStatus(BatchStatus.FAILED);
			StepExecution step = execution.createStepExecution("step1");
			step.setWriteCount(1);
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Album album = AlbumFixture.withId(AlbumFixture.create(artist), 3L);
			given(albumRepository.findByDiscogsMasterId(21247L)).willReturn(Optional.of(album));

			// when
			listener.afterJob(execution);

			// then
			verify(eventPublisher).publishEvent(any(NewPressingEvent.class));
		}
	}
}
