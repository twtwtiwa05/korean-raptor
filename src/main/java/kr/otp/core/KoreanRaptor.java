package kr.otp.core;

import kr.otp.osm.StreetNetwork;
import kr.otp.raptor.data.TransitData;
import kr.otp.raptor.spi.KoreanAccessEgress;
import kr.otp.raptor.spi.KoreanTransitDataProvider;
import kr.otp.raptor.spi.KoreanTripSchedule;

import org.opentripplanner.raptor.RaptorService;
import org.opentripplanner.raptor.api.model.RaptorAccessEgress;
import org.opentripplanner.raptor.api.path.RaptorPath;
import org.opentripplanner.raptor.api.request.RaptorEnvironment;
import org.opentripplanner.raptor.api.request.RaptorProfile;
import org.opentripplanner.raptor.api.request.RaptorRequest;
import org.opentripplanner.raptor.api.request.RaptorRequestBuilder;
import org.opentripplanner.raptor.api.request.RaptorTuningParameters;
import org.opentripplanner.raptor.api.model.SearchDirection;
import org.opentripplanner.raptor.api.response.RaptorResponse;
import org.opentripplanner.raptor.configure.RaptorConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/**
 * 한국형 Raptor 경로탐색 엔진.
 *
 * OTP Raptor 모듈을 래핑하여 한국 GTFS 데이터로 경로 탐색을 수행.
 * 좌표 기반 검색을 지원하며, 내부적으로 가까운 정류장을 찾아 Raptor를 실행.
 *
 * 사용법:
 * <pre>
 * KoreanRaptor raptor = new KoreanRaptor(transitData);
 * List<RaptorPath<KoreanTripSchedule>> paths = raptor.route(
 *     fromLat, fromLon,
 *     toLat, toLon,
 *     departureTime
 * );
 * </pre>
 */
public class KoreanRaptor {

    private static final Logger LOG = LoggerFactory.getLogger(KoreanRaptor.class);

    // 설정 상수 - 속도 최적화
    private static final double MAX_ACCESS_WALK_METERS = 400.0;   // 출발지에서 정류장까지 최대 도보 거리
    private static final double MAX_EGRESS_WALK_METERS = 400.0;   // 정류장에서 목적지까지 최대 도보 거리
    private static final double WALK_SPEED_MPS = 1.2;             // 도보 속도 (m/s)
    private static final int SEARCH_WINDOW_SECONDS = 900;         // 검색 시간 범위 (15분)
    private static final int MAX_RESULTS = 5;                     // 최대 결과 수
    private static final int MAX_ACCESS_STOPS = 5;                // 최대 출발 정류장 수
    private static final int MAX_EGRESS_STOPS = 5;                // 최대 도착 정류장 수

    private final TransitData transitData;
    private final KoreanTransitDataProvider provider;
    private final RaptorService<KoreanTripSchedule> raptorService;
    private final AccessEgressFinder accessEgressFinder;

    public KoreanRaptor(TransitData transitData) {
        this(transitData, null);
    }

    public KoreanRaptor(TransitData transitData, StreetNetwork streetNetwork) {
        this.transitData = transitData;
        this.provider = new KoreanTransitDataProvider(transitData);

        // Raptor 설정 생성 (기본값 사용)
        RaptorConfig<KoreanTripSchedule> config = new RaptorConfig<>(
            new RaptorTuningParameters() {},  // 기본 튜닝 파라미터
            new RaptorEnvironment() {}        // 기본 환경
        );
        this.raptorService = new RaptorService<>(config);
        this.accessEgressFinder = new AccessEgressFinder(transitData);

        // OSM 도로망 설정 (선택적)
        if (streetNetwork != null) {
            this.accessEgressFinder.setStreetNetwork(streetNetwork);
        }

        LOG.info("KoreanRaptor 초기화 완료: {} (OSM: {})", provider, isUsingOsm());
    }

    /**
     * OSM 기반 도보 경로 사용 여부
     */
    public boolean isUsingOsm() {
        return accessEgressFinder.isUsingOsm();
    }

    /**
     * 좌표 기반 경로 탐색
     *
     * @param fromLat 출발지 위도
     * @param fromLon 출발지 경도
     * @param toLat   목적지 위도
     * @param toLon   목적지 경도
     * @param departureTime 출발 시간 (초, 자정 기준. 예: 09:00 = 32400)
     * @return 탐색된 경로 목록 (Pareto 최적)
     */
    public List<RaptorPath<KoreanTripSchedule>> route(
        double fromLat, double fromLon,
        double toLat, double toLon,
        int departureTime
    ) {
        long startTime = System.currentTimeMillis();

        // 1. 출발지 근처 정류장 찾기 (Access) - 상위 N개만
        List<RaptorAccessEgress> accessPaths = accessEgressFinder.findAccess(
            fromLat, fromLon, MAX_ACCESS_WALK_METERS
        );
        if (accessPaths.isEmpty()) {
            LOG.warn("출발지 근처에 정류장이 없습니다: ({}, {})", fromLat, fromLon);
            return List.of();
        }
        if (accessPaths.size() > MAX_ACCESS_STOPS) {
            accessPaths = accessPaths.subList(0, MAX_ACCESS_STOPS);
        }

        // 2. 목적지 근처 정류장 찾기 (Egress) - 상위 N개만
        List<RaptorAccessEgress> egressPaths = accessEgressFinder.findEgress(
            toLat, toLon, MAX_EGRESS_WALK_METERS
        );
        if (egressPaths.isEmpty()) {
            LOG.warn("목적지 근처에 정류장이 없습니다: ({}, {})", toLat, toLon);
            return List.of();
        }
        if (egressPaths.size() > MAX_EGRESS_STOPS) {
            egressPaths = egressPaths.subList(0, MAX_EGRESS_STOPS);
        }

        LOG.debug("Access 정류장: {}개, Egress 정류장: {}개",
            accessPaths.size(), egressPaths.size());

        // 3. Raptor 요청 생성
        RaptorRequest<KoreanTripSchedule> request = buildRequest(
            accessPaths, egressPaths, departureTime
        );

        // 4. Raptor 실행
        RaptorResponse<KoreanTripSchedule> response = raptorService.route(request, provider);

        long elapsed = System.currentTimeMillis() - startTime;

        if (response.noConnectionFound()) {
            LOG.info("경로를 찾을 수 없습니다 ({}ms)", elapsed);
            return List.of();
        }

        Collection<RaptorPath<KoreanTripSchedule>> paths = response.paths();
        LOG.info("경로 {}개 발견 ({}ms)", paths.size(), elapsed);

        return List.copyOf(paths);
    }

    /**
     * 정류장 인덱스 기반 경로 탐색 (직접 지정)
     *
     * @param fromStopIndex 출발 정류장 인덱스
     * @param toStopIndex   도착 정류장 인덱스
     * @param departureTime 출발 시간 (초)
     * @return 탐색된 경로 목록
     */
    public List<RaptorPath<KoreanTripSchedule>> routeByStopIndex(
        int fromStopIndex,
        int toStopIndex,
        int departureTime
    ) {
        // Access: 출발 정류장에서 바로 탑승 (도보 0초)
        List<RaptorAccessEgress> accessPaths = List.of(
            new KoreanAccessEgress(fromStopIndex, 0, 0)
        );

        // Egress: 도착 정류장에서 바로 하차 (도보 0초)
        List<RaptorAccessEgress> egressPaths = List.of(
            new KoreanAccessEgress(toStopIndex, 0, 0)
        );

        RaptorRequest<KoreanTripSchedule> request = buildRequest(
            accessPaths, egressPaths, departureTime
        );

        RaptorResponse<KoreanTripSchedule> response = raptorService.route(request, provider);

        if (response.noConnectionFound()) {
            return List.of();
        }

        return List.copyOf(response.paths());
    }

    /**
     * Raptor 요청 빌드
     */
    private RaptorRequest<KoreanTripSchedule> buildRequest(
        List<RaptorAccessEgress> accessPaths,
        List<RaptorAccessEgress> egressPaths,
        int departureTime
    ) {
        RaptorRequestBuilder<KoreanTripSchedule> builder = new RaptorRequestBuilder<>();

        builder
            .profile(RaptorProfile.STANDARD)                  // 표준 모드 (빠름)
            .searchDirection(SearchDirection.FORWARD)   // 정방향 탐색
            .searchParams()
                .earliestDepartureTime(departureTime)
                .searchWindowInSeconds(SEARCH_WINDOW_SECONDS)
                .timetable(true)                        // 시간표 기반 검색
                .addAccessPaths(accessPaths)
                .addEgressPaths(egressPaths);

        // 추가 최적화: 최대 환승 횟수 제한
        builder.searchParams().numberOfAdditionalTransfers(3);  // 최대 3회 환승

        return builder.build();
    }

    /**
     * 정류장 이름 조회
     */
    public String getStopName(int stopIndex) {
        return transitData.getStopName(stopIndex);
    }

    /**
     * 정류장 개수
     */
    public int getStopCount() {
        return transitData.getStopCount();
    }

    /**
     * 노선 개수
     */
    public int getRouteCount() {
        return transitData.getRouteCount();
    }

    /**
     * TransitData 반환
     */
    public TransitData getTransitData() {
        return transitData;
    }

    /**
     * Provider 반환
     */
    public KoreanTransitDataProvider getProvider() {
        return provider;
    }

    @Override
    public String toString() {
        return String.format("KoreanRaptor[stops=%d, routes=%d, trips=%d]",
            transitData.getStopCount(),
            transitData.getRouteCount(),
            transitData.getTotalTripCount());
    }
}
