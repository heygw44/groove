package com.groove.file.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.groove.file.config.FileProperties;
import com.groove.fixture.FileFixture;

class LocalFileStorageTest {

	@TempDir
	Path tempDir;

	LocalFileStorage localFileStorage;

	@BeforeEach
	void setUp() {
		Path uploadRoot = tempDir.resolve("uploads");
		FileProperties fileProperties = new FileProperties(uploadRoot.toString(), "http://localhost:8080/uploads");
		localFileStorage = new LocalFileStorage(fileProperties);
		localFileStorage.init();
	}

	@Nested
	@DisplayName("init()")
	class Init {

		@Test
		@DisplayName("루트 디렉터리가 없으면 생성한다")
		void createsRootDirectoryWhenMissing() {
			// then
			assertThat(Files.isDirectory(tempDir.resolve("uploads"))).isTrue();
		}
	}

	@Nested
	@DisplayName("store()")
	class Store {

		@Test
		@DisplayName("yyyy/MM/dd 하위 폴더에 UUID 파일명으로 저장하고 내용이 일치한다")
		void storesFileUnderDateFolderWithUuidName() throws IOException {
			// given
			byte[] content = "image-bytes".getBytes();
			MockMultipartFile file = FileFixture.image("cover.png", "image/png", content);

			// when
			String key = localFileStorage.store(file, "png");

			// then
			String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
			assertThat(key).startsWith(today + "/");
			assertThat(key).endsWith(".png");

			Path saved = tempDir.resolve("uploads").resolve(key);
			assertThat(Files.exists(saved)).isTrue();
			assertThat(Files.readAllBytes(saved)).isEqualTo(content);
		}
	}
}
