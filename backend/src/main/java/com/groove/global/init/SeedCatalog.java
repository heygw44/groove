package com.groove.global.init;

import java.time.LocalDate;
import java.util.List;

/** {@link LocalDataInitializer} 가 사용하는 로컬 전용 더미 카탈로그 데이터. */
final class SeedCatalog {

	private SeedCatalog() {
	}

	record LabelSeed(String name, String country) {
	}

	record ArtistSeed(String name, String nameEn, String description) {
	}

	record AlbumSeed(String title, int artistIndex, Integer labelIndex, LocalDate releaseDate, String pressingInfo,
			String colorVariant, int price, String description, List<Integer> genreIndexes, int stock) {
	}

	static final List<String> GENRES = List.of(
			"Jazz", "Rock", "Hip-Hop", "K-Pop", "Soul", "Electronic", "Classical", "Folk", "Indie", "OST");

	static final List<LabelSeed> LABELS = List.of(
			new LabelSeed("Blue Note", "US"),
			new LabelSeed("ECM", "DE"),
			new LabelSeed("Motown", "US"),
			new LabelSeed("Columbia", "US"),
			new LabelSeed("Verve", "US"),
			new LabelSeed("Impulse!", "US"),
			new LabelSeed("Sub Pop", "US"),
			new LabelSeed("Warp", "UK"));

	static final List<ArtistSeed> ARTISTS = List.of(
			new ArtistSeed("Miles Davis", "Miles Davis", "재즈 트럼펫의 거장, 모달 재즈와 퓨전을 개척했다"),
			new ArtistSeed("John Coltrane", "John Coltrane", "테너 색소폰의 혁신가, 모드 재즈와 프리 재즈를 이끌었다"),
			new ArtistSeed("Bill Evans", "Bill Evans", "서정적인 피아노 보이싱으로 모던 재즈 피아노 트리오를 재정의했다"),
			new ArtistSeed("Keith Jarrett", "Keith Jarrett", "즉흥 연주의 대가, 솔로 피아노 콘서트로 유명하다"),
			new ArtistSeed("The Beatles", "The Beatles", "20세기 대중음악을 재편한 영국의 록 밴드"),
			new ArtistSeed("Pink Floyd", "Pink Floyd", "프로그레시브 록의 상징, 개념 앨범의 선구자"),
			new ArtistSeed("Radiohead", "Radiohead", "얼터너티브 록에서 전자음악까지 아우른 실험적 밴드"),
			new ArtistSeed("Arctic Monkeys", "Arctic Monkeys", "영국 인디 록 씬을 이끈 셰필드 출신 밴드"),
			new ArtistSeed("Kendrick Lamar", "Kendrick Lamar", "서사적 가사와 사회 비판으로 평단의 찬사를 받은 랩퍼"),
			new ArtistSeed("Nas", "Nas", "뉴욕 힙합의 클래식을 만든 스토리텔러"),
			new ArtistSeed("방탄소년단", "BTS", "전 세계적 인기를 얻은 한국의 보이 그룹"),
			new ArtistSeed("뉴진스", "NewJeans", "Y2K 감성으로 K-팝 트렌드를 이끈 걸그룹"),
			new ArtistSeed("Marvin Gaye", "Marvin Gaye", "소울 음악에 사회적 메시지를 담은 모타운의 간판스타"),
			new ArtistSeed("Stevie Wonder", "Stevie Wonder", "멀티 악기 연주자이자 소울/펑크의 혁신가"),
			new ArtistSeed("Daft Punk", "Daft Punk", "프렌치 하우스를 세계적으로 알린 일렉트로닉 듀오"),
			new ArtistSeed("Aphex Twin", "Aphex Twin", "IDM 장르를 개척한 실험적 전자음악가"),
			new ArtistSeed("Glenn Gould", "Glenn Gould", "바흐 해석으로 유명한 캐나다의 클래식 피아니스트"),
			new ArtistSeed("Bob Dylan", "Bob Dylan", "포크와 록을 잇는 가사의 시인"),
			new ArtistSeed("Nick Drake", "Nick Drake", "생전엔 조명받지 못한 영국 포크의 전설"),
			new ArtistSeed("久石譲", "Joe Hisaishi", "지브리 애니메이션 음악으로 유명한 일본의 작곡가"));

	static final List<AlbumSeed> ALBUMS = List.of(
			new AlbumSeed("Kind of Blue", 0, 3, LocalDate.of(1959, 8, 17), "180g", "Black",
					32000, "모달 재즈의 정점을 보여주는 마일스 데이비스의 대표작", List.of(0), 18),
			new AlbumSeed("Sketches of Spain", 0, 3, LocalDate.of(1960, 7, 18), "180g Heavyweight", "Clear",
					38000, "길 에반스와의 협업으로 만든 오케스트라 재즈", List.of(0), 12),
			new AlbumSeed("Bitches Brew", 0, 3, LocalDate.of(1970, 3, 30), "2LP 180g", "Black",
					45000, "재즈 퓨전의 문을 연 실험적 더블 앨범", List.of(0), 9),
			new AlbumSeed("Milestones", 0, 3, LocalDate.of(1958, 9, 8), "Mono Remaster", "Black",
					29000, "모달 재즈로의 전환점이 된 앨범", List.of(0), 20),
			new AlbumSeed("A Love Supreme", 1, 5, LocalDate.of(1965, 2, 1), "180g", "Gold",
					42000, "영적 탐구를 음악으로 승화시킨 콜트레인의 걸작", List.of(0), 15),
			new AlbumSeed("Giant Steps", 1, null, LocalDate.of(1960, 1, 27), "Half-Speed Master", "Blue Marble",
					36000, "빠른 코드 변화로 유명한 하드 밥의 교과서", List.of(0), 0),
			new AlbumSeed("Blue Train", 1, 0, LocalDate.of(1957, 9, 15), "180g", "Black",
					33000, "블루노트 레이블의 대표적인 하드 밥 앨범", List.of(0), 22),
			new AlbumSeed("Waltz for Debby", 2, 4, LocalDate.of(1961, 1, 1), "180g", "Clear",
					31000, "빌리지 뱅가드 라이브 실황을 담은 트리오의 명반", List.of(0), 14),
			new AlbumSeed("Portrait in Jazz", 2, 4, LocalDate.of(1960, 1, 1), "Limited Reissue", "Black",
					34000, "피아노 트리오의 상호작용을 재정의한 앨범", List.of(0), 11),
			new AlbumSeed("The Köln Concert", 3, 1, LocalDate.of(1975, 1, 1), "2LP 180g", "Black",
					48000, "완전 즉흥 솔로 피아노 콘서트 실황", List.of(0, 6), 25),
			new AlbumSeed("My Song", 3, 1, LocalDate.of(1978, 1, 1), "180g", "Smoke",
					37000, "유러피언 쿼르텟과의 서정적인 스튜디오 녹음", List.of(0), 10),
			new AlbumSeed("Abbey Road", 4, null, LocalDate.of(1969, 9, 26), "Half-Speed Master", "Black",
					49000, "비틀즈가 마지막으로 함께 녹음한 앨범", List.of(1), 30),
			new AlbumSeed("Revolver", 4, null, LocalDate.of(1966, 8, 5), "Mono Remaster", "Black",
					46000, "스튜디오 실험의 전환점이 된 앨범", List.of(1), 17),
			new AlbumSeed("Sgt. Pepper's Lonely Hearts Club Band", 4, null, LocalDate.of(1967, 6, 1), "180g", "Gold",
					52000, "콘셉트 앨범의 원형을 제시한 작품", List.of(1), 0),
			new AlbumSeed("The Dark Side of the Moon", 5, null, LocalDate.of(1973, 3, 1), "180g Heavyweight",
					"Clear", 44000, "프로그레시브 록의 상징적인 콘셉트 앨범", List.of(1), 28),
			new AlbumSeed("Wish You Were Here", 5, null, LocalDate.of(1975, 9, 12), "180g", "Black",
					41000, "상실과 그리움을 다룬 서정적인 록 앨범", List.of(1), 19),
			new AlbumSeed("OK Computer", 6, null, LocalDate.of(1997, 5, 21), "180g", "Black",
					39000, "밀레니엄 전환기의 불안을 담아낸 얼터너티브 록 걸작", List.of(1), 21),
			new AlbumSeed("Kid A", 6, null, LocalDate.of(2000, 10, 2), "Limited Reissue", "Smoke",
					43000, "일렉트로닉으로 방향을 튼 실험적인 앨범", List.of(1, 5), 13),
			new AlbumSeed("In Rainbows", 6, null, LocalDate.of(2007, 10, 10), "2LP 180g", "Red",
					40000, "자유 가격제 공개로 화제가 된 앨범", List.of(1, 8), 16),
			new AlbumSeed("AM", 7, null, LocalDate.of(2013, 9, 9), "180g", "Black",
					35000, "묵직한 그루브와 도시적 감성을 담은 앨범", List.of(1, 8), 24),
			new AlbumSeed("Whatever People Say I Am, That's What I'm Not", 7, null, LocalDate.of(2006, 1, 23),
					"Mono Remaster", "Clear", 33000, "데뷔작으로 영국 차트 신기록을 세운 앨범", List.of(1, 8), 18),
			new AlbumSeed("To Pimp a Butterfly", 8, null, LocalDate.of(2015, 3, 15), "2LP 180g", "Black",
					45000, "재즈와 펑크를 결합한 사회 비판적 랩 앨범", List.of(2), 20),
			new AlbumSeed("good kid, m.A.A.d city", 8, null, LocalDate.of(2012, 10, 22), "180g", "Blue Marble",
					38000, "컴튼에서의 성장기를 그린 컨셉 앨범", List.of(2), 15),
			new AlbumSeed("DAMN.", 8, null, LocalDate.of(2017, 4, 14), "Limited Reissue", "Red",
					41000, "퓰리처상을 수상한 랩 앨범", List.of(2), 0),
			new AlbumSeed("Illmatic", 9, 3, LocalDate.of(1994, 4, 19), "180g", "Black",
					37000, "뉴욕 힙합의 정수를 담은 데뷔작", List.of(2), 12),
			new AlbumSeed("It Was Written", 9, 3, LocalDate.of(1996, 7, 2), "Half-Speed Master", "Gold",
					39000, "마피오소 랩을 대중화시킨 두 번째 앨범", List.of(2), 9),
			new AlbumSeed("Love Yourself: Tear", 10, null, LocalDate.of(2018, 5, 18), "180g", "Clear",
					46000, "방탄소년단의 성장 서사를 담은 정규 3집", List.of(3), 27),
			new AlbumSeed("Map of the Soul: 7", 10, null, LocalDate.of(2020, 2, 21), "2LP 180g", "Black",
					55000, "자아 탐구를 주제로 한 정규 4집", List.of(3), 23),
			new AlbumSeed("Get Up", 11, null, LocalDate.of(2023, 7, 21), "Limited Reissue", "Gold",
					42000, "Y2K 감성을 담은 두 번째 EP", List.of(3), 30),
			new AlbumSeed("NewJeans", 11, null, LocalDate.of(2022, 8, 1), "180g", "Clear",
					39000, "데뷔와 동시에 신드롬을 일으킨 첫 EP", List.of(3), 26),
			new AlbumSeed("What's Going On", 12, 2, LocalDate.of(1971, 5, 21), "180g", "Black",
					40000, "베트남전 시대의 사회 문제를 노래한 소울 명반", List.of(4), 14),
			new AlbumSeed("Let's Get It On", 12, 2, LocalDate.of(1973, 8, 28), "Half-Speed Master", "Red",
					38000, "관능적인 소울의 정수를 보여주는 앨범", List.of(4), 11),
			new AlbumSeed("Songs in the Key of Life", 13, 2, LocalDate.of(1976, 9, 28), "2LP 180g", "Black",
					47000, "스티비 원더 창작력의 정점을 보여주는 더블 앨범", List.of(4), 17),
			new AlbumSeed("Innervisions", 13, 2, LocalDate.of(1973, 8, 3), "180g", "Gold",
					36000, "신디사이저를 적극 활용한 소울/펑크 명반", List.of(4), 13),
			new AlbumSeed("Talking Book", 13, 2, LocalDate.of(1972, 10, 27), "Mono Remaster", "Clear",
					34000, "Superstition 이 수록된 대표작", List.of(4), 19),
			new AlbumSeed("Discovery", 14, 7, LocalDate.of(2001, 3, 12), "180g", "Blue Marble",
					43000, "디스코와 일렉트로닉을 결합한 대표작", List.of(5), 21),
			new AlbumSeed("Random Access Memories", 14, 3, LocalDate.of(2013, 5, 17), "2LP 180g", "Black",
					52000, "라이브 세션과 일렉트로닉을 접목한 앨범", List.of(5), 16),
			new AlbumSeed("Homework", 14, 7, LocalDate.of(1997, 1, 20), "Limited Reissue", "Smoke",
					41000, "프렌치 하우스의 시작을 알린 데뷔작", List.of(5), 0),
			new AlbumSeed("Selected Ambient Works 85-92", 15, 7, LocalDate.of(1992, 2, 10), "180g", "Clear",
					44000, "IDM 장르의 초석을 놓은 앰비언트 명반", List.of(5), 12),
			new AlbumSeed("Selected Ambient Works Volume II", 15, 7, LocalDate.of(1994, 3, 7), "2LP 180g", "Smoke",
					46000, "더 몽환적이고 추상적인 사운드스케이프", List.of(5), 10),
			new AlbumSeed("Goldberg Variations (1981)", 16, 3, LocalDate.of(1982, 1, 1), "180g", "Black",
					39000, "바흐 골드베르크 변주곡의 재해석 녹음", List.of(6), 8),
			new AlbumSeed("Goldberg Variations (1955)", 16, 3, LocalDate.of(1956, 1, 1), "Mono Remaster", "Black",
					37000, "글렌 굴드의 데뷔 녹음이자 클래식 명반", List.of(6), 7),
			new AlbumSeed("Highway 61 Revisited", 17, 3, LocalDate.of(1965, 8, 30), "180g", "Black",
					36000, "포크에서 록으로 전환하는 밥 딜런의 대표작", List.of(7, 1), 15),
			new AlbumSeed("Blood on the Tracks", 17, 3, LocalDate.of(1975, 1, 20), "Half-Speed Master", "Red",
					38000, "이별과 상실을 노래한 서정적인 포크 록 앨범", List.of(7, 1), 13),
			new AlbumSeed("Bringing It All Back Home", 17, 3, LocalDate.of(1965, 3, 22), "Mono Remaster", "Clear",
					35000, "일렉트릭 사운드를 처음 도입한 앨범", List.of(7, 1), 18),
			new AlbumSeed("Pink Moon", 18, null, LocalDate.of(1972, 2, 25), "180g", "Black",
					33000, "간결한 어쿠스틱 기타로 완성한 마지막 앨범", List.of(7), 9),
			new AlbumSeed("Five Leaves Left", 18, null, LocalDate.of(1969, 7, 3), "Limited Reissue", "Clear",
					34000, "닉 드레이크의 서정적인 데뷔작", List.of(7), 11),
			new AlbumSeed("Bryter Layter", 18, null, LocalDate.of(1970, 11, 1), "180g", "Gold",
					35000, "재즈와 포크가 어우러진 두 번째 앨범", List.of(7), 0),
			new AlbumSeed("Spirited Away (Soundtrack)", 19, null, LocalDate.of(2001, 7, 18), "180g", "Clear",
					42000, "센과 치히로의 행방불명 오리지널 사운드트랙", List.of(9, 6), 26),
			new AlbumSeed("My Neighbor Totoro (Soundtrack)", 19, null, LocalDate.of(1988, 4, 16), "Limited Reissue",
					"Blue Marble", 40000, "이웃집 토토로 오리지널 사운드트랙", List.of(9, 6), 22));
}
