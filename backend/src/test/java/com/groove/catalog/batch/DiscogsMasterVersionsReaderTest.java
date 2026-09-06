package com.groove.catalog.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;

import com.groove.catalog.client.PressingLookupClient;
import com.groove.catalog.client.dto.DiscogsMasterVersionsResponse.Version;
import com.groove.fixture.DiscogsFixture;

class DiscogsMasterVersionsReaderTest {

	private static final long MASTER_ID = 21247L;

	private final PressingLookupClient client = mock(PressingLookupClient.class);

	@Nested
	@DisplayName("read()")
	class Read {

		@Test
		@DisplayName("여러 페이지를 순서대로 읽고 마지막에는 null 을 반환한다")
		void readsAcrossPagesThenReturnsNull() {
			// given
			Version v1 = DiscogsFixture.version(1L, "Album");
			Version v2 = DiscogsFixture.version(2L, "Album");
			Version v3 = DiscogsFixture.version(3L, "Album");
			when(client.getMasterVersions(MASTER_ID, 0))
					.thenReturn(DiscogsFixture.masterVersionsResponse(0, 2, List.of(v1, v2)));
			when(client.getMasterVersions(MASTER_ID, 1))
					.thenReturn(DiscogsFixture.masterVersionsResponse(1, 2, List.of(v3)));
			DiscogsMasterVersionsReader reader = new DiscogsMasterVersionsReader(client, MASTER_ID);
			reader.open(new ExecutionContext());

			// when & then
			assertThat(reader.read()).isEqualTo(v1);
			assertThat(reader.read()).isEqualTo(v2);
			assertThat(reader.read()).isEqualTo(v3);
			assertThat(reader.read()).isNull();
		}

		@Test
		@DisplayName("마지막 페이지의 versions 가 비어 있으면 null 을 반환한다")
		void returnsNullWhenLastPageEmpty() {
			// given
			when(client.getMasterVersions(MASTER_ID, 0))
					.thenReturn(DiscogsFixture.masterVersionsResponse(0, 1, List.of()));
			DiscogsMasterVersionsReader reader = new DiscogsMasterVersionsReader(client, MASTER_ID);
			reader.open(new ExecutionContext());

			// when & then
			assertThat(reader.read()).isNull();
		}

		@Test
		@DisplayName("ExecutionContext 에 저장된 위치부터 읽고 이전 페이지는 다시 조회하지 않는다")
		void restoresPositionAndSkipsPreviousPages() {
			// given
			Version v2 = DiscogsFixture.version(2L, "Album");
			ExecutionContext context = new ExecutionContext();
			context.putInt(DiscogsMasterVersionsReader.PAGE_KEY, 0);
			context.putInt(DiscogsMasterVersionsReader.INDEX_KEY, 1);
			when(client.getMasterVersions(MASTER_ID, 0))
					.thenReturn(DiscogsFixture.masterVersionsResponse(0, 1,
							List.of(DiscogsFixture.version(1L, "Album"), v2)));
			DiscogsMasterVersionsReader reader = new DiscogsMasterVersionsReader(client, MASTER_ID);
			reader.open(context);

			// when
			Version result = reader.read();

			// then
			assertThat(result).isEqualTo(v2);
			verify(client, never()).getMasterVersions(MASTER_ID, 1);
		}

		@Test
		@DisplayName("복원된 인덱스가 페이지 끝이면 다음 페이지로 넘어간다")
		void movesToNextPageWhenRestoredIndexAtEnd() {
			// given
			Version v2 = DiscogsFixture.version(2L, "Album");
			ExecutionContext context = new ExecutionContext();
			context.putInt(DiscogsMasterVersionsReader.PAGE_KEY, 0);
			context.putInt(DiscogsMasterVersionsReader.INDEX_KEY, 1);
			when(client.getMasterVersions(MASTER_ID, 0))
					.thenReturn(DiscogsFixture.masterVersionsResponse(0, 2,
							List.of(DiscogsFixture.version(1L, "Album"))));
			when(client.getMasterVersions(MASTER_ID, 1))
					.thenReturn(DiscogsFixture.masterVersionsResponse(1, 2, List.of(v2)));
			DiscogsMasterVersionsReader reader = new DiscogsMasterVersionsReader(client, MASTER_ID);
			reader.open(context);

			// when
			Version result = reader.read();

			// then
			assertThat(result).isEqualTo(v2);
			verify(client, times(1)).getMasterVersions(MASTER_ID, 0);
			verify(client, times(1)).getMasterVersions(MASTER_ID, 1);
		}
	}

	@Nested
	@DisplayName("update()")
	class Update {

		@Test
		@DisplayName("현재 page/index 를 ExecutionContext 에 저장한다")
		void persistsPageAndIndex() {
			// given
			Version v1 = DiscogsFixture.version(1L, "Album");
			Version v2 = DiscogsFixture.version(2L, "Album");
			when(client.getMasterVersions(MASTER_ID, 0))
					.thenReturn(DiscogsFixture.masterVersionsResponse(0, 1, List.of(v1, v2)));
			DiscogsMasterVersionsReader reader = new DiscogsMasterVersionsReader(client, MASTER_ID);
			ExecutionContext context = new ExecutionContext();
			reader.open(context);
			reader.read();

			// when
			reader.update(context);

			// then
			assertThat(context.getInt(DiscogsMasterVersionsReader.PAGE_KEY)).isZero();
			assertThat(context.getInt(DiscogsMasterVersionsReader.INDEX_KEY)).isOne();
		}
	}
}
