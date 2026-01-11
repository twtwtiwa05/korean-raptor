package kr.otp.raptor.data;

import kr.otp.raptor.spi.KoreanRoute;
import kr.otp.raptor.spi.KoreanTransfer;

import java.util.Iterator;
import java.util.List;

/**
 * Raptor 알고리즘용 대중교통 데이터 컨테이너.
 *
 * GTFS 데이터를 Raptor가 요구하는 형태로 변환하여 저장.
 * 모든 데이터는 인덱스 기반으로 접근 (성능 최적화).
 *
 * 구조:
 *   - stops: 정류장 정보 (인덱스로 접근)
 *   - routes: 노선 정보 (패턴 + 시간표)
 *   - transfers: 환승 정보 (정류장별)
 *   - routesByStop: 정류장별 경유 노선 인덱스
 */
public class TransitData {

    // 정류장 정보
    private final int stopCount;
    private final String[] stopNames;          // 정류장 이름 (인덱스로 접근)
    private final double[] stopLats;           // 위도
    private final double[] stopLons;           // 경도

    // 노선 정보
    private final KoreanRoute[] routes;

    // 환승 정보 (정류장별)
    private final List<KoreanTransfer>[] transfersFromStop;
    private final List<KoreanTransfer>[] transfersToStop;

    // 정류장별 경유 노선 인덱스
    private final int[][] routesByStop;

    // 서비스 시간 범위
    private final int serviceStartTime;  // 예: 04:00 = 14400
    private final int serviceEndTime;    // 예: 26:00 = 93600

    @SuppressWarnings("unchecked")
    public TransitData(
        int stopCount,
        String[] stopNames,
        double[] stopLats,
        double[] stopLons,
        KoreanRoute[] routes,
        List<KoreanTransfer>[] transfersFromStop,
        List<KoreanTransfer>[] transfersToStop,
        int[][] routesByStop,
        int serviceStartTime,
        int serviceEndTime
    ) {
        this.stopCount = stopCount;
        this.stopNames = stopNames;
        this.stopLats = stopLats;
        this.stopLons = stopLons;
        this.routes = routes;
        this.transfersFromStop = transfersFromStop;
        this.transfersToStop = transfersToStop;
        this.routesByStop = routesByStop;
        this.serviceStartTime = serviceStartTime;
        this.serviceEndTime = serviceEndTime;
    }

    // ═══════════════════════════════════════════════════════════════
    // 정류장 정보
    // ═══════════════════════════════════════════════════════════════

    public int getStopCount() {
        return stopCount;
    }

    public String getStopName(int stopIndex) {
        if (stopIndex < 0 || stopIndex >= stopNames.length) {
            return "Unknown(" + stopIndex + ")";
        }
        return stopNames[stopIndex];
    }

    public double getStopLat(int stopIndex) {
        return stopLats[stopIndex];
    }

    public double getStopLon(int stopIndex) {
        return stopLons[stopIndex];
    }

    // ═══════════════════════════════════════════════════════════════
    // 노선 정보
    // ═══════════════════════════════════════════════════════════════

    public int getRouteCount() {
        return routes.length;
    }

    public KoreanRoute getRoute(int routeIndex) {
        return routes[routeIndex];
    }

    // ═══════════════════════════════════════════════════════════════
    // 환승 정보
    // ═══════════════════════════════════════════════════════════════

    public Iterator<KoreanTransfer> getTransfersFrom(int stopIndex) {
        if (stopIndex < 0 || stopIndex >= transfersFromStop.length) {
            return java.util.Collections.emptyIterator();
        }
        List<KoreanTransfer> transfers = transfersFromStop[stopIndex];
        return transfers != null ? transfers.iterator() : java.util.Collections.emptyIterator();
    }

    public Iterator<KoreanTransfer> getTransfersTo(int stopIndex) {
        if (stopIndex < 0 || stopIndex >= transfersToStop.length) {
            return java.util.Collections.emptyIterator();
        }
        List<KoreanTransfer> transfers = transfersToStop[stopIndex];
        return transfers != null ? transfers.iterator() : java.util.Collections.emptyIterator();
    }

    // ═══════════════════════════════════════════════════════════════
    // 정류장별 노선
    // ═══════════════════════════════════════════════════════════════

    public int[] getRoutesByStop(int stopIndex) {
        if (stopIndex < 0 || stopIndex >= routesByStop.length) {
            return new int[0];
        }
        int[] routes = routesByStop[stopIndex];
        return routes != null ? routes : new int[0];
    }

    // ═══════════════════════════════════════════════════════════════
    // 서비스 시간
    // ═══════════════════════════════════════════════════════════════

    public int getServiceStartTime() {
        return serviceStartTime;
    }

    public int getServiceEndTime() {
        return serviceEndTime;
    }

    // ═══════════════════════════════════════════════════════════════
    // 통계
    // ═══════════════════════════════════════════════════════════════

    public int getPatternCount() {
        return routes.length;  // 1 route = 1 pattern (현재 구조)
    }

    public int getTotalTripCount() {
        int count = 0;
        for (KoreanRoute route : routes) {
            count += route.getTripCount();
        }
        return count;
    }

    @Override
    public String toString() {
        return String.format("TransitData[stops=%d, routes=%d, trips=%d, service=%s~%s]",
            stopCount, routes.length, getTotalTripCount(),
            formatTime(serviceStartTime), formatTime(serviceEndTime));
    }

    private String formatTime(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        return String.format("%02d:%02d", h, m);
    }
}
