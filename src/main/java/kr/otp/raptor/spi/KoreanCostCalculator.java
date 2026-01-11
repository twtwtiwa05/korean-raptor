package kr.otp.raptor.spi;

import org.opentripplanner.raptor.api.model.RaptorAccessEgress;
import org.opentripplanner.raptor.api.model.RaptorTransferConstraint;
import org.opentripplanner.raptor.spi.RaptorCostCalculator;

/**
 * 한국 대중교통용 비용 계산기.
 *
 * Multi-Criteria 탐색에서 경로의 "비용"을 계산.
 * 비용은 시간 + 환승 페널티 등으로 구성.
 *
 * 단위: centi-seconds (1초 = 100)
 * 예: 1분 = 6000 centi-seconds
 *
 * 비용 구성:
 * - 승차 비용 (첫 승차 / 환승)
 * - 대기 시간 비용
 * - 탑승 시간 비용
 * - Egress 비용
 */
public class KoreanCostCalculator implements RaptorCostCalculator<KoreanTripSchedule> {

    // 비용 계수 (centi-seconds 단위, 1초 = 100)
    private static final int FIRST_BOARD_COST = 60 * 100;     // 첫 승차: 1분 패널티
    private static final int TRANSFER_COST = 120 * 100;       // 환승: 2분 패널티
    private static final double WAIT_RELUCTANCE = 1.0;        // 대기 시간 가중치

    @Override
    public int boardingCost(
        boolean firstBoarding,
        int prevArrivalTime,
        int boardStop,
        int boardTime,
        KoreanTripSchedule trip,
        RaptorTransferConstraint transferConstraints
    ) {
        // 대기 시간 계산
        int waitTime = boardTime - prevArrivalTime;
        int waitCost = (int) (waitTime * 100 * WAIT_RELUCTANCE);

        // 첫 승차 vs 환승
        int boardCost = firstBoarding ? FIRST_BOARD_COST : TRANSFER_COST;

        return boardCost + waitCost;
    }

    @Override
    public int onTripRelativeRidingCost(int boardTime, KoreanTripSchedule tripScheduledBoarded) {
        // 탑승 중 추가 비용 없음
        // 필요시 여기에 교통수단별 추가 비용 적용 가능
        return ZERO_COST;
    }

    @Override
    public int transitArrivalCost(
        int boardCost,
        int alightSlack,
        int transitTime,
        KoreanTripSchedule trip,
        int toStop
    ) {
        // 총 비용 = 승차 비용 + 탑승 시간 비용
        // alightSlack은 별도로 처리됨 (시간에 이미 포함)
        return boardCost + (transitTime * 100);
    }

    @Override
    public int waitCost(int waitTimeInSeconds) {
        return (int) (waitTimeInSeconds * 100 * WAIT_RELUCTANCE);
    }

    @Override
    public int calculateRemainingMinCost(int minTravelTime, int minNumTransfers, int fromStop) {
        // 남은 최소 비용 추정 (휴리스틱)
        // 실제보다 작거나 같아야 함 (Admissible Heuristic)
        return (minTravelTime * 100) + (minNumTransfers * TRANSFER_COST);
    }

    @Override
    public int costEgress(RaptorAccessEgress egress) {
        // Egress 비용 = 기본 비용 그대로
        // 필요시 추가 비용 적용 가능 (예: FLEX 서비스)
        return egress.c1();
    }

    @Override
    public String toString() {
        return "KoreanCostCalculator{" +
            "firstBoardCost=" + (FIRST_BOARD_COST / 100) + "s, " +
            "transferCost=" + (TRANSFER_COST / 100) + "s, " +
            "waitReluctance=" + WAIT_RELUCTANCE +
            '}';
    }
}
