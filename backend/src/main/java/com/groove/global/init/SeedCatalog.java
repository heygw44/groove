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
			new LabelSeed("Warp", "UK"),
			new LabelSeed("Def Jam", "US"),
			new LabelSeed("XL Recordings", "UK"),
			new LabelSeed("4AD", "UK"),
			new LabelSeed("Rough Trade", "UK"),
			new LabelSeed("Atlantic", "US"),
			new LabelSeed("Deutsche Grammophon", "DE"),
			new LabelSeed("Ninja Tune", "UK"),
			new LabelSeed("Stones Throw", "US"));

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
			new ArtistSeed("久石譲", "Joe Hisaishi", "지브리 애니메이션 음악으로 유명한 일본의 작곡가"),
			new ArtistSeed("Thelonious Monk", "Thelonious Monk", "불협화음과 독특한 리듬으로 재즈 피아노를 새로 쓴 연주자"),
			new ArtistSeed("Herbie Hancock", "Herbie Hancock", "모달 재즈부터 퓨전까지 넘나든 다재다능한 피아니스트"),
			new ArtistSeed("Charles Mingus", "Charles Mingus", "격정적인 편곡과 베이스 연주로 유명한 작곡가 겸 밴드리더"),
			new ArtistSeed("Nina Simone", "Nina Simone", "재즈와 소울, 저항의 메시지를 함께 담아낸 보컬리스트"),
			new ArtistSeed("Led Zeppelin", "Led Zeppelin", "하드 록의 원형을 완성한 영국의 록 밴드"),
			new ArtistSeed("Fleetwood Mac", "Fleetwood Mac", "내부 갈등을 명반으로 승화시킨 영미 합작 록 밴드"),
			new ArtistSeed("The Rolling Stones", "The Rolling Stones", "블루스에 뿌리를 둔 로큰롤을 대중화시킨 밴드"),
			new ArtistSeed("Nirvana", "Nirvana", "그런지를 주류로 끌어올린 시애틀 출신 밴드"),
			new ArtistSeed("Queen", "Queen", "웅장한 스타디움 록을 완성한 영국의 록 밴드"),
			new ArtistSeed("David Bowie", "David Bowie", "끊임없는 변신으로 록의 경계를 넓힌 아티스트"),
			new ArtistSeed("Jay-Z", "Jay-Z", "뉴욕 힙합을 대표하는 랩퍼이자 프로듀서"),
			new ArtistSeed("OutKast", "OutKast", "서던 힙합의 지평을 넓힌 애틀랜타 듀오"),
			new ArtistSeed("A Tribe Called Quest", "A Tribe Called Quest", "재즈와 힙합을 결합한 얼터너티브 힙합의 선구자"),
			new ArtistSeed("Wu-Tang Clan", "Wu-Tang Clan", "로파이 프로덕션으로 힙합 클래식을 만든 뉴욕 집단"),
			new ArtistSeed("Kanye West", "Kanye West", "소울 샘플링과 실험적 프로덕션으로 힙합을 재정의한 아티스트"),
			new ArtistSeed("블랙핑크", "Blackpink", "글로벌 인기를 얻은 한국의 4인조 걸그룹"),
			new ArtistSeed("아이유", "IU", "싱어송라이터로서 입지를 다진 한국의 솔로 아티스트"),
			new ArtistSeed("세븐틴", "Seventeen", "자체 프로듀싱으로 유명한 한국의 보이 그룹"),
			new ArtistSeed("에스파", "aespa", "메타버스 세계관을 앞세운 한국의 걸그룹"),
			new ArtistSeed("Aretha Franklin", "Aretha Franklin", "소울의 여왕이라 불리는 보컬리스트"),
			new ArtistSeed("Sam Cooke", "Sam Cooke", "소울 음악의 기틀을 놓은 초창기 보컬리스트"),
			new ArtistSeed("Otis Redding", "Otis Redding", "스택스 사운드를 대표하는 소울 싱어"),
			new ArtistSeed("Boards of Canada", "Boards of Canada", "노스탤지어 사운드로 유명한 앰비언트 일렉트로닉 듀오"),
			new ArtistSeed("Burial", "Burial", "덥스텝을 예술적 경지로 끌어올린 익명의 프로듀서"),
			new ArtistSeed("Four Tet", "Four Tet", "포크와 일렉트로닉을 넘나든 영국의 프로듀서"),
			new ArtistSeed("Yo-Yo Ma", "Yo-Yo Ma", "폭넓은 레퍼토리로 유명한 첼리스트"),
			new ArtistSeed("Ludovico Einaudi", "Ludovico Einaudi", "미니멀한 피아노 선율로 대중적 인기를 얻은 작곡가"),
			new ArtistSeed("Simon & Garfunkel", "Simon & Garfunkel", "포크 록 하모니를 완성한 미국의 듀오"),
			new ArtistSeed("Joni Mitchell", "Joni Mitchell", "고백적 가사로 포크 음악의 전형을 만든 싱어송라이터"),
			new ArtistSeed("Vampire Weekend", "Vampire Weekend", "아프로팝 리듬을 접목한 뉴욕의 인디 록 밴드"),
			new ArtistSeed("Tame Impala", "Tame Impala", "사이키델릭과 신스 팝을 오간 호주의 프로젝트 밴드"),
			new ArtistSeed("The Strokes", "The Strokes", "2000년대 개러지 록 부흥을 이끈 뉴욕의 밴드"),
			new ArtistSeed("Beach House", "Beach House", "드림 팝의 정서를 대표하는 미국의 듀오"),
			new ArtistSeed("Alt-J", "Alt-J", "독특한 화성 진행으로 주목받은 영국의 밴드"),
			new ArtistSeed("The National", "The National", "절제된 편곡과 서정성으로 알려진 미국의 밴드"),
			new ArtistSeed("Fleet Foxes", "Fleet Foxes", "합창 하모니가 돋보이는 포크 록 밴드"),
			new ArtistSeed("Bon Iver", "Bon Iver", "오두막에서의 고립을 음악으로 담아낸 싱어송라이터"),
			new ArtistSeed("Hans Zimmer", "Hans Zimmer", "웅장한 스코어로 유명한 영화음악 작곡가"),
			new ArtistSeed("Ennio Morricone", "Ennio Morricone", "마카로니 웨스턴 음악의 거장으로 불리는 작곡가"),
			new ArtistSeed("John Williams", "John Williams", "할리우드 블록버스터 음악을 대표하는 작곡가"));
}
