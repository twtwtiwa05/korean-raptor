package kr.otp.raptor.data;

import kr.otp.gtfs.GtfsBundle;
import kr.otp.gtfs.model.GtfsRoute;
import kr.otp.gtfs.model.GtfsStop;
import kr.otp.gtfs.model.GtfsStopTime;
import kr.otp.gtfs.model.GtfsTrip;
import kr.otp.raptor.spi.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * GTFS 데이터를 Raptor용 TransitData로 변환하는 빌더.
 *
 * 변환 과정:
 * 1. stopId → stopIndex 매핑 생성
 * 2. 트립들을 정류장 순서(패턴)별로 그룹화
 * 3. 각 패턴에 대해 KoreanTripPattern, KoreanTimeTable, KoreanRoute 생성
 * 4. routesByStop 인덱스 생성
 * 5. 환승 정보 생성 (거리 기반)
 */
public class TransitDataBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(TransitDataBuilder.class);

    // 환승 생성 설정
    private static final double MAX_TRANSFER_DISTANCE_METERS = 500.0;  // 최대 환승 거리
    private static final double WALK_SPEED_MPS = 1.2;                  // 도보 속도 (m/s)

    private final GtfsBundle gtfs;

    // 빌드 결과
    private Map<String, Integer> stopIdToIndex;
    private String[] stopNames;
    private double[] stopLats;
    private double[] stopLons;
    private List<KoreanRoute> routes;
    private List<KoreanTransfer>[] transfersFromStop;
    private List<KoreanTransfer>[] transfersToStop;
    private int[][] routesByStop;

    public TransitDataBuilder(GtfsBundle gtfs) {
        this.gtfs = gtfs;
    }

    /**
     * TransitData 빌드
     */
    public TransitData build() {
        long startTime = System.currentTimeMillis();

        // 1. 정류장 인덱스 매핑
        LOG.info("정류장 인덱스 생성 중...");
        buildStopIndex();
        LOG.info("  정류장: {}개", stopIdToIndex.size());

        // 2. 패턴 그룹화 및 노선 생성
        LOG.info("패턴 그룹화 중...");
        buildRoutesFromPatterns();
        LOG.info("  노선(패턴): {}개", routes.size());

        // 3. 정류장별 노선 인덱스 생성
        LOG.info("정류장별 노선 인덱스 생성 중...");
        buildRoutesByStop();

        // 4. 환승 정보 생성
        LOG.info("환승 정보 생성 중...");
        buildTransfers();
        int transferCount = countTransfers();
        LOG.info("  환승: {}개", transferCount);

        long elapsed = System.currentTimeMillis() - startTime;
        LOG.info("TransitData 빌드 완료 ({}ms)", elapsed);

        return new TransitData(
            stopIdToIndex.size(),
            stopNames,
            stopLats,
            stopLons,
            routes.toArray(new KoreanRoute[0]),
            transfersFromStop,
            transfersToStop,
            routesByStop,
            gtfs.getServiceStartTime(),
            gtfs.getServiceEndTime()
        );
    }

    /**
     * 정류장 인덱스 매핑 생성
     */
    private void buildStopIndex() {
        Collection<GtfsStop> stops = gtfs.getAllStops();
        int count = stops.size();

        stopIdToIndex = new HashMap<>(count);
        stopNames = new String[count];
        stopLats = new double[count];
        stopLons = new double[count];

        int index = 0;
        for (GtfsStop stop : stops) {
            stopIdToIndex.put(stop.stopId(), index);
            stopNames[index] = stop.stopName();
            stopLats[index] = stop.lat();
            stopLons[index] = stop.lon();
            index++;
        }
    }

    /**
     * 트립을 패턴별로 그룹화하여 노선 생성
     */
    private void buildRoutesFromPatterns() {
        routes = new ArrayList<>();

        // 패턴 키 → 트립 리스트
        Map<String, List<TripWithStopTimes>> patternGroups = new LinkedHashMap<>();

        // 모든 트립을 패턴별로 그룹화
        for (GtfsTrip trip : gtfs.getAllTrips()) {
            List<GtfsStopTime> stopTimes = gtfs.getStopTimes(trip.tripId());
            if (stopTimes == null || stopTimes.isEmpty()) {
                continue;
            }

            // 패턴 키 생성: routeId + 정류장 순서
            String patternKey = buildPatternKey(trip.routeId(), stopTimes);

            patternGroups.computeIfAbsent(patternKey, k -> new ArrayList<>())
                .add(new TripWithStopTimes(trip, stopTimes));
        }

        LOG.debug("패턴 그룹 수: {}", patternGroups.size());

        // 각 패턴 그룹에서 Route 생성
        int patternIndex = 0;
        for (Map.Entry<String, List<TripWithStopTimes>> entry : patternGroups.entrySet()) {
            List<TripWithStopTimes> trips = entry.getValue();
            if (trips.isEmpty()) continue;

            KoreanRoute route = buildRoute(patternIndex, trips);
            if (route != null) {
                routes.add(route);
                patternIndex++;
            }
        }
    }

    /**
     * 패턴 키 생성 (routeId + stopId 순서)
     */
    private String buildPatternKey(String routeId, List<GtfsStopTime> stopTimes) {
        StringBuilder sb = new StringBuilder(routeId);
        sb.append(":");
        for (GtfsStopTime st : stopTimes) {
            sb.append(st.stopId()).append(",");
        }
        return sb.toString();
    }

    /**
     * 패턴 그룹에서 Route 생성
     */
    private KoreanRoute buildRoute(int patternIndex, List<TripWithStopTimes> trips) {
        TripWithStopTimes firstTrip = trips.get(0);
        List<GtfsStopTime> firstStopTimes = firstTrip.stopTimes;
        GtfsRoute gtfsRoute = gtfs.getRoute(firstTrip.trip.routeId());

        if (gtfsRoute == null) {
            LOG.warn("노선 정보 없음: {}", firstTrip.trip.routeId());
            return null;
        }

        // 정류장 인덱스 배열 생성
        int[] stopIndexes = new int[firstStopTimes.size()];
        for (int i = 0; i < firstStopTimes.size(); i++) {
            String stopId = firstStopTimes.get(i).stopId();
            Integer idx = stopIdToIndex.get(stopId);
            if (idx == null) {
                LOG.warn("정류장 인덱스 없음: {}", stopId);
                return null;
            }
            stopIndexes[i] = idx;
        }

        // 패턴 생성
        int slackIndex = gtfsRoute.getSlackIndex();
        String debugInfo = gtfsRoute.getRouteTypeName() + "_" + gtfsRoute.getDisplayName();
        KoreanTripPattern pattern = new KoreanTripPattern(
            patternIndex,
            stopIndexes,
            slackIndex,
            debugInfo
        );

        // 스케줄 생성 및 정렬
        List<KoreanTripSchedule> schedules = new ArrayList<>();
        for (TripWithStopTimes tripData : trips) {
            KoreanTripSchedule schedule = buildSchedule(tripData, pattern, gtfsRoute);
            if (schedule != null) {
                schedules.add(schedule);
            }
        }

        if (schedules.isEmpty()) {
            return null;
        }

        // 첫 출발 시간으로 정렬
        schedules.sort(Comparator.comparingInt(KoreanTripSchedule::getFirstDepartureTime));

        // TimeTable 생성
        KoreanTimeTable timetable = new KoreanTimeTable(
            schedules.toArray(new KoreanTripSchedule[0])
        );

        // Route 생성
        return new KoreanRoute(
            pattern,
            timetable,
            gtfsRoute.routeId(),
            gtfsRoute.routeShortName(),
            gtfsRoute.routeLongName(),
            gtfsRoute.routeType()
        );
    }

    /**
     * 트립 스케줄 생성
     */
    private KoreanTripSchedule buildSchedule(
        TripWithStopTimes tripData,
        KoreanTripPattern pattern,
        GtfsRoute gtfsRoute
    ) {
        List<GtfsStopTime> stopTimes = tripData.stopTimes;
        int stopCount = stopTimes.size();

        int[] arrivalTimes = new int[stopCount];
        int[] departureTimes = new int[stopCount];

        for (int i = 0; i < stopCount; i++) {
            GtfsStopTime st = stopTimes.get(i);
            arrivalTimes[i] = st.arrivalTime();
            departureTimes[i] = st.departureTime();

            // 시간이 없으면 스킵
            if (arrivalTimes[i] < 0 || departureTimes[i] < 0) {
                return null;
            }
        }

        // 정렬 인덱스 = 첫 정류장 출발 시간
        int tripSortIndex = departureTimes[0];

        return new KoreanTripSchedule(
            tripSortIndex,
            arrivalTimes,
            departureTimes,
            pattern,
            tripData.trip.tripId(),
            gtfsRoute.getDisplayName()
        );
    }

    /**
     * 정류장별 경유 노선 인덱스 생성
     */
    @SuppressWarnings("unchecked")
    private void buildRoutesByStop() {
        int stopCount = stopIdToIndex.size();

        // 정류장별 노선 목록 (임시)
        List<Integer>[] routeListsByStop = new List[stopCount];
        for (int i = 0; i < stopCount; i++) {
            routeListsByStop[i] = new ArrayList<>();
        }

        // 각 노선의 정류장들에 노선 인덱스 추가
        for (int routeIndex = 0; routeIndex < routes.size(); routeIndex++) {
            KoreanRoute route = routes.get(routeIndex);
            KoreanTripPattern pattern = (KoreanTripPattern) route.pattern();

            for (int stopIdx : pattern.getStopIndexes()) {
                if (!routeListsByStop[stopIdx].contains(routeIndex)) {
                    routeListsByStop[stopIdx].add(routeIndex);
                }
            }
        }

        // int[][] 배열로 변환
        routesByStop = new int[stopCount][];
        for (int i = 0; i < stopCount; i++) {
            List<Integer> routeList = routeListsByStop[i];
            routesByStop[i] = routeList.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    /**
     * 환승 정보 생성 (거리 기반)
     */
    @SuppressWarnings("unchecked")
    private void buildTransfers() {
        int stopCount = stopIdToIndex.size();
        transfersFromStop = new List[stopCount];
        transfersToStop = new List[stopCount];

        for (int i = 0; i < stopCount; i++) {
            transfersFromStop[i] = new ArrayList<>();
            transfersToStop[i] = new ArrayList<>();
        }

        // 간단한 거리 기반 환승 생성
        // 성능을 위해 공간 인덱스(R-tree) 사용하면 좋지만, 일단 단순 구현
        // 실제로는 너무 느릴 수 있으므로, 필요시 최적화 필요

        LOG.info("  환승 거리 기반 생성 (최대 {}m)", MAX_TRANSFER_DISTANCE_METERS);

        // 정류장 인덱스 리스트
        List<StopLocation> stopLocations = new ArrayList<>(stopCount);
        for (int i = 0; i < stopCount; i++) {
            stopLocations.add(new StopLocation(i, stopLats[i], stopLons[i]));
        }

        // 위도 기준 정렬 (공간 인덱스 대용)
        stopLocations.sort(Comparator.comparingDouble(s -> s.lat));

        // 거리 기반 환승 생성
        for (int i = 0; i < stopCount; i++) {
            StopLocation from = stopLocations.get(i);

            // 근처 정류장만 검색 (위도 기준 필터링)
            double latDiff = MAX_TRANSFER_DISTANCE_METERS / 111000.0; // 약 111km per degree

            for (int j = i + 1; j < stopCount; j++) {
                StopLocation to = stopLocations.get(j);

                // 위도 차이가 너무 크면 종료
                if (to.lat - from.lat > latDiff) {
                    break;
                }

                // 거리 계산
                double distance = haversineDistance(from.lat, from.lon, to.lat, to.lon);

                if (distance <= MAX_TRANSFER_DISTANCE_METERS) {
                    int duration = (int) Math.ceil(distance / WALK_SPEED_MPS);

                    // 양방향 환승 추가
                    KoreanTransfer transferTo = new KoreanTransfer(to.stopIndex, duration, distance);
                    KoreanTransfer transferFrom = new KoreanTransfer(from.stopIndex, duration, distance);

                    transfersFromStop[from.stopIndex].add(transferTo);
                    transfersToStop[to.stopIndex].add(transferFrom);

                    transfersFromStop[to.stopIndex].add(transferFrom);
                    transfersToStop[from.stopIndex].add(transferTo);
                }
            }

            // 진행 상황 로깅 (10% 단위)
            if (i > 0 && i % (stopCount / 10) == 0) {
                LOG.debug("  환승 생성 진행: {}%", (i * 100) / stopCount);
            }
        }
    }

    /**
     * Haversine 거리 계산
     */
    private static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000; // 지구 반지름 (미터)

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    private int countTransfers() {
        int count = 0;
        for (List<KoreanTransfer> transfers : transfersFromStop) {
            if (transfers != null) {
                count += transfers.size();
            }
        }
        return count;
    }

    // ═══════════════════════════════════════════════════════════════
    // 내부 클래스
    // ═══════════════════════════════════════════════════════════════

    private static class TripWithStopTimes {
        final GtfsTrip trip;
        final List<GtfsStopTime> stopTimes;

        TripWithStopTimes(GtfsTrip trip, List<GtfsStopTime> stopTimes) {
            this.trip = trip;
            this.stopTimes = stopTimes;
        }
    }

    private static class StopLocation {
        final int stopIndex;
        final double lat;
        final double lon;

        StopLocation(int stopIndex, double lat, double lon) {
            this.stopIndex = stopIndex;
            this.lat = lat;
            this.lon = lon;
        }
    }
}
