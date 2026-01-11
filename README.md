# Korean Raptor

**한국 전국 대중교통 경로탐색 엔진** - OTP Raptor 기반

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey.svg)]()

> 출발/도착 좌표만 입력하면 **0.5초 내**에 최적 대중교통 경로를 찾아주는 CLI 엔진

### Based on

이 프로젝트는 [OpenTripPlanner](https://github.com/opentripplanner/OpenTripPlanner)의 **Raptor 모듈**을 사용합니다.

- **Raptor 알고리즘**: Microsoft Research의 [RAPTOR: Round-Based Public Transit Routing](https://www.microsoft.com/en-us/research/publication/round-based-public-transit-routing/) (2012) 기반
- **구현체**: OTP Raptor JAR (LGPL v3 라이선스)를 그대로 사용하고, SPI 인터페이스만 한국 GTFS에 맞게 구현

---

## Documentation

- **[시작 가이드](docs/GETTING_STARTED.md)** - 설치부터 실행까지 상세 안내
- **[기술 아키텍처](docs/TECHNICAL_ARCHITECTURE.md)** - 시스템 설계, SPI 구현, OSM 통합, 성능 최적화 상세
- **[한계점 및 향후 개선](docs/LIMITATIONS_AND_FUTURE_WORK.md)** - 현재 제한사항, 개선 로드맵
- [설정 옵션](#configuration)
- [아키텍처](#architecture)

---

## Features

- **전국 대중교통 지원**: 버스, 지하철, KTX/SRT, 일반철도
- **초고속 검색**: ~0.5초 (21만 정류장, 35만 트립)
- **실제 도보 경로**: OSM 기반 A* 알고리즘 (선택적)
- **좌표 기반 검색**: 위도/경도 입력으로 가까운 정류장 자동 탐색
- **CLI & 대화형 모드**: 간편한 사용

---

## Demo

```
═══════════════════════════════════════════════════════════════
           한국형 Raptor 경로탐색 엔진 v1.0.0-SNAPSHOT
═══════════════════════════════════════════════════════════════

[1/4] GTFS 데이터 로드 중...
  완료: 212,105 정류장, 27,138 노선, 349,580 트립 (8.2초)
[2/4] Raptor 데이터 구조 생성 중...
  완료: 32,229 패턴, 349,509 트립 (4.1초)
[3/4] OSM 도로망 로드 중...
  완료: StreetNetwork[nodes=15711249, edges=3547892] (45.3초)
[4/4] Raptor 엔진 초기화...
  완료: KoreanRaptor[stops=212105, routes=32229, trips=349509]
  도보 거리: OSM 기반 (실제 도로)

═══════════════════════════════════════════════════════════════
  초기화 완료! (58.2초)
═══════════════════════════════════════════════════════════════

[결과수: 5] > 37.5547 126.9707 37.4979 127.0276 09:00 5
검색: (37.5547, 126.9707) → (37.4979, 127.0276) @ 09:00 이후, 최대 5개
─────────────────────────────────────────────────────────────────
09:00 이후 경로 5개 (전체 5개, 0.487초)

■ 경로 1: 09:03 출발 → 09:45 도착 (42분, 환승 2회)
  1. 도보 3분 → 서울역버스환승센터
  2. [750B] 서울역버스환승센터 09:10 → 숙대입구역 09:13
  3. 환승 도보 1분
  4. [서울4호선] 숙대입구 09:18 → 사당 09:32
  5. 환승 도보 0분
  6. [서울2호선] 사당 09:36 → 강남 09:45
  7. 도보 0분 → 목적지
```

---

## Installation

### 1. 요구사항

- **Java 21** 이상
- **40GB+ RAM** (OSM 사용 시) / 8GB (OSM 미사용)
- **GTFS 데이터** (한국 전국)

### 2. 빌드

```bash
git clone https://github.com/twtwtiwa05/korean-raptor.git
cd korean-raptor

# Fat JAR 빌드
./gradlew fatJar   # Linux/macOS
gradlew.bat fatJar # Windows
```

### 3. 데이터 준비

#### GTFS 데이터 (필수)
```
data/gtfs/
├── stops.txt
├── routes.txt
├── trips.txt
├── stop_times.txt
├── calendar.txt
└── transfers.txt
```

#### OSM 데이터 (선택적 - 실제 도보 경로)
```bash
mkdir -p data/osm
curl -L -o data/osm/south-korea.osm.pbf \
  https://download.geofabrik.de/asia/south-korea-latest.osm.pbf
```

---

## Usage

### 커맨드라인 실행

```bash
# Windows
search.cmd [출발위도] [출발경도] [도착위도] [도착경도] [시간] [결과수]

# 예시: 서울역 → 강남역, 09:00 출발
search.cmd 37.5547 126.9707 37.4979 127.0276 09:00 5
```

### 대화형 모드

```bash
search.cmd

[결과수: 5] > 37.5547 126.9707 37.4979 127.0276 09:00
[결과수: 5] > n=10          # 결과 개수 변경
[결과수: 10] > q            # 종료
```

### 테스트 좌표

| 구간 | 출발 | 도착 | 명령어 |
|------|------|------|--------|
| 서울역→강남역 | 37.5547, 126.9707 | 37.4979, 127.0276 | `37.5547 126.9707 37.4979 127.0276 09:00` |
| 서울역→부산역 | 37.5547, 126.9707 | 35.1150, 129.0410 | `37.5547 126.9707 35.1150 129.0410 09:00` |
| 홍대→잠실 | 37.5571, 126.9244 | 37.5133, 127.1001 | `37.5571 126.9244 37.5133 127.1001 09:00` |

---

## Performance

### 데이터 규모

| 항목 | 수량 |
|------|------|
| 정류장 | 212,105개 |
| 노선(패턴) | 32,229개 |
| 트립 | 349,509개 |
| 환승 | 2,027,380개 |
| OSM 노드 | 15,711,249개 |

### 검색 성능

| 모드 | 검색 시간 | 비고 |
|------|----------|------|
| OSM 모드 | ~0.5초 | 실제 도보 경로 |
| Haversine 모드 | ~0.4초 | 직선 거리 |

### 최적화 기법

- **Raptor STANDARD 프로파일**: 빠른 단일 경로 검색
- **병렬 A* 실행**: 멀티코어 활용 (8코어)
- **HashMap 기반 A***: 15M 노드 초기화 제거
- **정류장-노드 사전 매핑**: 초기화 시 1회 계산

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Korean Raptor Engine                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  [좌표 입력]                                                 │
│       │                                                     │
│       ▼                                                     │
│  ┌─────────────────────┐     ┌─────────────────────┐       │
│  │ AccessEgressFinder  │────▶│   WalkingRouter     │       │
│  │ (가까운 정류장 탐색)  │     │   (A* 병렬 실행)     │       │
│  └─────────────────────┘     └─────────────────────┘       │
│       │                              │                      │
│       │                              ▼                      │
│       │                      ┌─────────────────────┐       │
│       │                      │   StreetNetwork     │       │
│       │                      │   (15M 노드 그래프)   │       │
│       │                      └─────────────────────┘       │
│       │                              ▲                      │
│       │                              │                      │
│       │                      ┌─────────────────────┐       │
│       │                      │     OsmLoader       │       │
│       │                      │  (south-korea.pbf)  │       │
│       │                      └─────────────────────┘       │
│       │                                                     │
│       ▼                                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                  OTP Raptor JAR                      │   │
│  │              (STANDARD 프로파일, 15분 윈도우)          │   │
│  └─────────────────────────────────────────────────────┘   │
│       │                                                     │
│       ▼                                                     │
│  [경로 결과 출력]                                            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
korean-otp/
├── build.gradle                 # Gradle 빌드 설정
├── search.cmd                   # 실행 스크립트 (Windows)
├── search.ps1                   # 실행 스크립트 (PowerShell)
│
├── libs/                        # OTP Raptor JAR
│   ├── raptor-*.jar
│   └── utils-*.jar
│
├── data/
│   ├── gtfs/                    # GTFS 데이터
│   └── osm/                     # OSM 데이터 (선택적)
│
└── src/main/java/kr/otp/
    ├── Main.java                # CLI 진입점
    │
    ├── core/
    │   ├── KoreanRaptor.java    # 메인 엔진
    │   └── AccessEgressFinder.java
    │
    ├── osm/                     # OSM 도보 경로
    │   ├── OsmLoader.java
    │   ├── StreetNetwork.java
    │   ├── StreetNode.java
    │   ├── StreetEdge.java
    │   └── WalkingRouter.java
    │
    ├── gtfs/                    # GTFS 모델 & 로더
    │   ├── model/
    │   ├── loader/
    │   └── GtfsBundle.java
    │
    └── raptor/
        ├── data/                # TransitData
        └── spi/                 # OTP SPI 구현체
```

---

## Configuration

### Raptor 설정 (`KoreanRaptor.java`)

```java
MAX_ACCESS_WALK_METERS = 400.0   // 최대 도보 거리 (m)
SEARCH_WINDOW_SECONDS = 900      // 검색 시간 범위 (15분)
MAX_ACCESS_STOPS = 5             // 출발 정류장 후보 수
MAX_EGRESS_STOPS = 5             // 도착 정류장 후보 수
numberOfAdditionalTransfers(3)   // 최대 환승 횟수
```

### A* 설정 (`WalkingRouter.java`)

```java
MAX_SEARCH_DISTANCE = 500.0      // 최대 탐색 거리 (m)
maxIterations = 15000            // 최대 반복 횟수
WALK_SPEED_MPS = 1.2             // 도보 속도 (m/s)
```

---

## Dependencies

- [OTP Raptor](https://github.com/opentripplanner/OpenTripPlanner) - 경로탐색 알고리즘
- [osm4j](https://github.com/topobyte/osm4j) - OSM 파싱
- [OpenCSV](https://opencsv.sourceforge.net/) - GTFS CSV 파싱
- [SLF4J](https://www.slf4j.org/) + [Logback](https://logback.qos.ch/) - 로깅

---

## References

- [RAPTOR Algorithm Paper](https://www.microsoft.com/en-us/research/publication/round-based-public-transit-routing/) - Microsoft Research, 2012
- [OpenTripPlanner](https://www.opentripplanner.org/) - 오픈소스 대중교통 플래너
- [GTFS Specification](https://gtfs.org/schedule/reference/) - General Transit Feed Specification
- [OpenStreetMap](https://www.openstreetmap.org/) - 오픈소스 지도 데이터

---

## Author

**김태우 (Taewoo Kim)**

- 가천대학교 학부생
- CAMMUS 연구원
- GitHub: [@twtwtiwa05](https://github.com/twtwtiwa05)
- Email: twdaniel@gachon.ac.kr

---

## License

이 프로젝트의 소스 코드는 **MIT License**로 배포됩니다 - [LICENSE](LICENSE) 참조.

**의존성 라이선스:**
- OTP Raptor JAR (`libs/`): [LGPL v3](https://www.gnu.org/licenses/lgpl-3.0.html) - OpenTripPlanner 프로젝트

---

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## Acknowledgments

- [OpenTripPlanner](https://github.com/opentripplanner/OpenTripPlanner) 팀의 Raptor 알고리즘 구현
- [Geofabrik](https://download.geofabrik.de/) OSM 데이터 제공
- 한국 공공데이터포털 GTFS 데이터

---

**Made with ❤️ for Korean public transit**
