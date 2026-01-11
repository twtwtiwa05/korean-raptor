# 한계점 및 향후 개선사항

본 문서는 Korean Raptor 프로젝트의 현재 한계점과 향후 개선 방향을 정리합니다.

---

## 목차

1. [현재 한계점](#1-현재-한계점)
2. [향후 개선사항](#2-향후-개선사항)
3. [기술적 부채](#3-기술적-부채)

---

## 1. 현재 한계점

### 1.1 Raptor 프로파일 제한

#### 문제점

현재 **STANDARD 프로파일**을 사용하여 검색 속도 ~0.5초를 달성했습니다.
그러나 이로 인해 **다양한 경로 옵션 제공이 제한**됩니다.

| 프로파일 | 검색 시간 | 결과 특성 |
|----------|----------|----------|
| STANDARD | ~0.5초 | 각 출발 시간의 최단 도착 경로만 |
| MULTI_CRITERIA | ~14초 | 파레토 최적 경로 (시간/환승/비용 다양) |

#### 구체적 차이

**STANDARD 결과:**
```
09:03 출발 → 09:45 도착 (환승 2회) ← 가장 빠름
09:07 출발 → 09:48 도착 (환승 2회) ← 가장 빠름
```

**MULTI_CRITERIA 결과 (제공 못함):**
```
09:03 출발 → 09:45 도착 (환승 2회) ← 가장 빠름
09:03 출발 → 09:52 도착 (환승 1회) ← 환승 적음
09:03 출발 → 10:05 도착 (환승 0회) ← 직행
```

#### 원인 분석

MULTI_CRITERIA가 느린 이유:

```
STANDARD:
  정류장당 상태 = 1개 (최단 시간)
  비교 연산 = O(1)

MULTI_CRITERIA:
  정류장당 상태 = 파레토 집합 (5~20개)
  비교 연산 = O(파레토 집합 크기) × 3가지 기준

  → 총 연산량 15~60배 증가
```

OTP 내부 `ParetoSet` 구현:
```java
// 새 경로마다 기존 모든 해와 비교
for (int i = 0; i < size; ++i) {
    boolean leftDominance = leftDominanceExist(newValue, elements[i]);
    boolean rightDominance = rightDominanceExist(newValue, elements[i]);
    // 3가지 기준(시간, 환승, 비용)으로 지배 관계 판정
}
```

### 1.2 OSM 메모리 사용량

#### 문제점

OSM 기반 도보 경로 사용 시 **40GB+ RAM**이 필요합니다.

| 모드 | 메모리 | 정확도 |
|------|--------|--------|
| Haversine (직선) | 8GB | 보통 |
| OSM (실제 도로) | 40GB+ | 높음 |

#### 원인

```
한국 OSM 데이터:
- 노드: 15,711,249개
- 엣지: 3,547,892개
- 각 노드: ~100 bytes (좌표, 엣지 리스트)
- 공간 인덱스: ~500MB

총 메모리: ~15GB (그래프) + ~10GB (JVM 오버헤드) + ~10GB (여유)
```

### 1.3 초기화 시간

#### 문제점

서버 시작 시 **~60초**의 초기화 시간이 필요합니다.

| 단계 | 시간 | 내용 |
|------|------|------|
| GTFS 로드 | ~8초 | 21만 정류장, 35만 트립 |
| TransitData 빌드 | ~4초 | 패턴 그룹화, 환승 생성 |
| OSM 로드 | ~45초 | 15M 노드 그래프 구축 |
| 엔진 초기화 | ~3초 | 정류장-노드 매핑 |

### 1.4 실시간 데이터 미지원

현재 **정적 GTFS**만 지원하며, 실시간 정보(GTFS-RT)는 지원하지 않습니다.

- 실시간 도착 정보 미반영
- 지연/운휴 정보 미반영
- 실시간 혼잡도 미반영

### 1.5 경로 품질 제한

#### 도보 경로 정확도

- A* 탐색 거리 제한: 500m
- 최대 반복 횟수: 15,000
- 제한 초과 시 직선 거리 × 1.3 사용

#### 환승 정보

- 거리 기반 환승만 지원 (500m 이내)
- 실제 환승 통로/시간 미반영
- 동일 역사 내 환승 최적화 없음

---

## 2. 향후 개선사항

### 2.1 MULTI_CRITERIA 성능 개선 (우선순위: 높음)

#### 방안 1: STANDARD 다중 실행 + 병합

```java
// 외부에서 구현 가능
List<Path> fastPaths = raptor.route(STANDARD);  // 0.3초
raptor.setTransferPenalty(300);  // 환승 페널티
List<Path> lowTransferPaths = raptor.route(STANDARD);  // 0.3초
List<Path> combined = mergeAndDeduplicate(fastPaths, lowTransferPaths);
// 총 ~0.6초로 다양한 옵션 제공
```

#### 방안 2: 파라미터 최적화

```java
// 검색 범위 축소
builder.searchParams()
    .numberOfAdditionalTransfers(2)  // 3 → 2
    .searchWindowInSeconds(300);      // 900 → 300
// 예상: 14초 → 3~5초
```

#### 방안 3: OTP 내부 수정 (장기)

```java
// ParetoSet 크기 제한
private static final int MAX_PARETO_SIZE = 5;

// 휴리스틱 가지치기
if (estimatedArrival > bestArrival * 1.3) {
    prune();
}
```

### 2.2 메모리 최적화 (우선순위: 중간)

#### 방안 1: OSM 그래프 압축

```java
// 현재: HashMap<Long, StreetNode>
// 개선: 배열 기반 저장 (메모리 50% 절감)
long[] nodeIds;
double[] lats;
double[] lons;
int[][] adjacencyList;
```

#### 방안 2: 지역별 분할 로딩

```java
// 서울/경기만 로드
OsmLoader loader = new OsmLoader(osmPath)
    .withBoundingBox(37.0, 126.5, 38.0, 127.5);
```

#### 방안 3: 메모리 맵 파일 (mmap)

```java
// 디스크 기반 그래프 (느리지만 메모리 절약)
MappedByteBuffer graphBuffer = fileChannel.map(...);
```

### 2.3 실시간 데이터 지원 (우선순위: 중간)

#### GTFS-RT 연동

```java
// 실시간 업데이트
GtfsRealtimeLoader rtLoader = new GtfsRealtimeLoader(rtUrl);
rtLoader.onUpdate(update -> {
    transitData.applyRealtimeUpdate(update);
});
```

#### 지원 예정 기능

- TripUpdate: 지연/운휴 정보
- VehiclePosition: 차량 위치
- Alert: 서비스 알림

### 2.4 API 서버화 (우선순위: 중간)

#### REST API

```
GET /api/v1/route?
    from=37.5547,126.9707&
    to=37.4979,127.0276&
    time=09:00&
    date=2026-01-12
```

#### 응답 형식

```json
{
  "routes": [
    {
      "departureTime": "09:03",
      "arrivalTime": "09:45",
      "duration": 42,
      "transfers": 2,
      "legs": [...]
    }
  ],
  "searchTime": 0.487
}
```

### 2.5 정류장 검색 최적화 (우선순위: 낮음)

#### R-tree 공간 인덱스

```java
// 현재: 선형 검색 O(N)
for (int i = 0; i < stopCount; i++) {
    if (distance(lat, lon, stopLats[i], stopLons[i]) < maxDistance) {
        candidates.add(i);
    }
}

// 개선: R-tree O(log N)
RTree<Integer> stopIndex = RTree.create();
List<Integer> candidates = stopIndex.search(
    Geometries.circle(lat, lon, maxDistance)
);
```

### 2.6 멀티모달 지원 (우선순위: 낮음)

#### 추가 교통수단

- 공유 자전거 (따릉이 등)
- 전동 킥보드
- 택시/카풀

---

## 3. 기술적 부채

### 3.1 테스트 부족

- 단위 테스트 미작성
- 통합 테스트 미작성
- 성능 벤치마크 미구축

### 3.2 에러 처리

- GTFS 파일 형식 오류 시 상세 메시지 부족
- OSM 파일 손상 시 복구 로직 없음
- 검색 실패 시 대안 경로 제안 없음

### 3.3 로깅/모니터링

- 검색 성능 메트릭 수집 없음
- 오류 추적 시스템 미연동
- 사용 통계 수집 없음

### 3.4 문서화

- API 문서 (Javadoc) 부족
- 코드 주석 일부 누락
- 아키텍처 결정 기록(ADR) 없음

---

## 개선 로드맵

| 단계 | 내용 | 예상 효과 |
|------|------|----------|
| Phase 1 | STANDARD 다중 실행 + 병합 | 다양한 경로 옵션 (~0.6초) |
| Phase 2 | API 서버화 (Spring Boot) | 웹/앱 연동 가능 |
| Phase 3 | GTFS-RT 연동 | 실시간 정보 반영 |
| Phase 4 | 메모리 최적화 | 16GB RAM으로 실행 가능 |
| Phase 5 | MULTI_CRITERIA 최적화 | 파레토 경로 2~3초 |

---

## 참고

- [OTP Raptor 소스코드](https://github.com/opentripplanner/OpenTripPlanner/tree/dev-2.x/raptor)
- [GTFS-RT Specification](https://gtfs.org/realtime/)
- [R-tree 라이브러리](https://github.com/davidmoten/rtree)

---

**작성일**: 2026-01
**작성자**: 김태우 (가천대학교 CAMMUS 연구원)
