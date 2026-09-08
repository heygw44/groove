package com.groove.file.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

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
import org.springframework.web.multipart.MultipartFile;

import com.groove.file.config.FileProperties;
import com.groove.fixture.FileFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

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

		@Test
		@DisplayName("루트 경로 자리에 파일이 있어 디렉터리를 만들 수 없으면 FILE_UPLOAD_FAILED 예외를 던진다")
		void throwsWhenRootDirectoryCreationFails() throws IOException {
			// given: 업로드 루트가 되어야 할 경로에 이미 일반 파일이 있어 디렉터리 생성이 막힌다
			Path blocked = tempDir.resolve("blocked-file");
			Files.createFile(blocked);
			FileProperties fileProperties = new FileProperties(blocked.toString(), "http://localhost:8080/uploads");
			LocalFileStorage storage = new LocalFileStorage(fileProperties);

			// when & then
			assertThatThrownBy(storage::init)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.FILE_UPLOAD_FAILED);
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

		@Test
		@DisplayName("파일 전송이 실패하면 FILE_UPLOAD_FAILED 예외를 던진다")
		void throwsWhenTransferFails() throws IOException {
			// given
			MultipartFile file = mock(MultipartFile.class);
			doThrow(new IOException("전송 실패")).when(file).transferTo(any(Path.class));

			// when & then
			assertThatThrownBy(() -> localFileStorage.store(file, "png"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.FILE_UPLOAD_FAILED);
		}
	}
}
