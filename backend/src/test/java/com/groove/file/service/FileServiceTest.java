package com.groove.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.groove.file.config.FileProperties;
import com.groove.file.dto.FileUploadResponse;
import com.groove.file.storage.FileStorage;
import com.groove.fixture.FileFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

	@Mock
	FileStorage fileStorage;

	FileService fileService;

	@BeforeEach
	void setUp() {
		FileProperties fileProperties = new FileProperties("./uploads", "http://localhost:8080/uploads");
		fileService = new FileService(fileStorage, fileProperties);
	}

	@Nested
	@DisplayName("uploadImage()")
	class UploadImage {

		@Test
		@DisplayName("정상 이미지면 url/originalName/size 를 반환한다")
		void returnsUploadResponse() {
			// given
			MockMultipartFile file = FileFixture.image();
			given(fileStorage.store(eq(file), eq("jpg"))).willReturn("2026/09/02/uuid.jpg");

			// when
			FileUploadResponse response = fileService.uploadImage(file);

			// then
			assertThat(response.url()).isEqualTo("http://localhost:8080/uploads/2026/09/02/uuid.jpg");
			assertThat(response.originalName()).isEqualTo("cover.jpg");
			assertThat(response.size()).isEqualTo(file.getSize());
		}

		@ParameterizedTest
		@DisplayName("허용 확장자면 확장자를 그대로 저장소에 전달한다")
		@CsvSource({
			"cover.jpg, image/jpeg, jpg",
			"cover.jpeg, image/jpeg, jpeg",
			"cover.png, image/png, png",
			"cover.webp, image/webp, webp"
		})
		void storesWithAllowedExtension(String filename, String contentType, String extension) {
			// given
			MockMultipartFile file = FileFixture.image(filename, contentType, "content".getBytes());
			given(fileStorage.store(any(), eq(extension))).willReturn("2026/09/02/uuid." + extension);

			// when
			fileService.uploadImage(file);

			// then
			verify(fileStorage).store(eq(file), eq(extension));
		}

		@ParameterizedTest
		@DisplayName("허용되지 않은 확장자면 FILE_INVALID_FORMAT 예외를 던진다")
		@ValueSource(strings = {"gif", "svg", "exe"})
		void throwsWhenExtensionNotAllowed(String extension) {
			// given
			MockMultipartFile file = FileFixture.image("cover." + extension, "image/jpeg", "content".getBytes());

			// when & then
			assertThatThrownBy(() -> fileService.uploadImage(file))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.FILE_INVALID_FORMAT);
		}

		@Test
		@DisplayName("확장자가 없으면 FILE_INVALID_FORMAT 예외를 던진다")
		void throwsWhenNoExtension() {
			// given
			MockMultipartFile file = FileFixture.image("cover", "image/jpeg", "content".getBytes());

			// when & then
			assertThatThrownBy(() -> fileService.uploadImage(file))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.FILE_INVALID_FORMAT);
		}

		@Test
		@DisplayName("확장자와 Content-Type 이 불일치하면 FILE_INVALID_FORMAT 예외를 던진다")
		void throwsWhenContentTypeMismatch() {
			// given
			MockMultipartFile file = FileFixture.image("cover.png", "image/jpeg", "content".getBytes());

			// when & then
			assertThatThrownBy(() -> fileService.uploadImage(file))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.FILE_INVALID_FORMAT);
		}

		@Test
		@DisplayName("Content-Type 이 이미지가 아니면 FILE_INVALID_FORMAT 예외를 던진다")
		void throwsWhenContentTypeNotImage() {
			// given
			MockMultipartFile file = FileFixture.image("cover.jpg", "text/plain", "content".getBytes());

			// when & then
			assertThatThrownBy(() -> fileService.uploadImage(file))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.FILE_INVALID_FORMAT);
		}

		@Test
		@DisplayName("5MB 를 초과하면 FILE_SIZE_EXCEEDED 예외를 던진다")
		void throwsWhenSizeExceeded() {
			// given
			MockMultipartFile file = FileFixture.largeImage();

			// when & then
			assertThatThrownBy(() -> fileService.uploadImage(file))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
		}

		@Test
		@DisplayName("빈 파일이면 FILE_EMPTY 예외를 던진다")
		void throwsWhenEmpty() {
			// given
			MockMultipartFile file = FileFixture.emptyImage();

			// when & then
			assertThatThrownBy(() -> fileService.uploadImage(file))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.FILE_EMPTY);
		}
	}
}
