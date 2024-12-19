package io.hhplus.tdd.point;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PointService 통합 테스트
 *
 * 통합 테스트는 성공 테스트 위주로 검증 하였습니다.
 * -> 실패 테스트는 단위 테스트에서 모두 작성했기 때문에
 */
@SpringBootTest
public class PointServiceIntegrationTest {

    private static final long ZERO = 0L;

    @Autowired
    private PointService pointService;

    /**
     * 테이스 케이스 작성 이유
     * -> userPointTable 과의 상호작용이 정상 검증
     */
    @Test
    void 포인트_조회_성공() {
        // given
        long userId = 1L;
        // when
        UserPoint userPoint = pointService.findUserPointById(userId);
        // then
        assertThat(userPoint).isNotNull();
        assertThat(userPoint.id()).isEqualTo(userId);
    }

    /**
     * 테스트 케이스 작성 이유
     * -> 충전/사용 기록이 있는 유저의 내역이 정상 조회 되는지 검증
     */
    @Test
    void 포인트_이용내역_조회_성공() {
        // given
        long userId = 2L;
        long chargeAmount = 2000L;
        long useAmount = 1000L;
        pointService.chargeUserPoint(userId, chargeAmount);
        pointService.useUserPoint(userId, useAmount);

        // when
        List<PointHistory> pointHistories = pointService.findPointHistoriesById(userId);

        // then
        PointHistory chargeHistory = pointHistories.stream()
                .filter(pointHistory -> pointHistory.type() == TransactionType.CHARGE)
                .findFirst()
                .orElse(null);

        PointHistory useHistory = pointHistories.stream()
                .filter(pointHistory -> pointHistory.type() == TransactionType.USE)
                .findFirst()
                .orElse(null);

        assertThat(pointHistories.size()).isEqualTo(2L);
        assertThat(chargeHistory).isNotNull();
        assertThat(useHistory).isNotNull();
        assertThat(chargeHistory.amount() - useHistory.amount()).isEqualTo(chargeAmount - useAmount);
    }

    /**
     * 테스트 케이스 작성 이유
     * -> 같은 유저가 동시에 100번 충전 요청 했을경우 데이터 정합성 검증
     */
    @Test
    void 포인트_충전_동시성_100번_성공() throws InterruptedException {
        // given
        long userId = 3L;
        long amount = 500L;
        int threads = 100;

        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        pointService.chargeUserPoint(userId,amount);

        // when
        for (int i = 0; i < threads; i++) {
            executorService.submit(() -> {
                try {
                    pointService.chargeUserPoint(userId, amount);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        assertThat(pointService.findUserPointById(userId).point()).isEqualTo(amount * threads + amount);
    }

    /**
     * 테스트 케이스 작성 이유
     * -> 서로 다른 유저가 동시에 50번씩 충전 요청 했을경우 각 유저의 데이터 정합성 검증
     */
    @Test
    void 유저2명_포인트_충전_동시성_50번_성공() throws InterruptedException {
        // given
        final long user1 = 4L;
        final long amount = 500L;
        final int threads = 100;

        final long user2 = 5L;
        final long amount2 = 300L;

        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        pointService.chargeUserPoint(user1,amount);
        pointService.chargeUserPoint(user2,amount2);

        // when
        for (int i = 0; i < threads; i++) {
            executorService.submit(() -> {
                try {
                    pointService.chargeUserPoint(user1, amount);
                    pointService.chargeUserPoint(user2, amount2);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        assertThat(pointService.findUserPointById(user1).point()).isEqualTo(amount * threads + amount);
        assertThat(pointService.findUserPointById(user2).point()).isEqualTo(amount2 * threads + amount2);
    }

    /**
     * 테스트 케이스 작성 이유
     * -> 같은 유저가 동시에 100번 사용 요청 했을경우 데이터 정합성 검증
     */
    @Test
    void 포인트_사용_동시성_100번_성공() throws InterruptedException {
        // given
        final long userId = 6L;
        final long amount = 20L;
        final int threads = 100;

        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        pointService.chargeUserPoint(userId,2000L);

        // when
        for (int i = 0; i < threads; i++) {
            executorService.submit(() -> {
                try {
                    pointService.useUserPoint(userId, amount);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        assertThat(pointService.findUserPointById(userId).point()).isEqualTo(ZERO);
    }

    /**
     * 테스트 케이스 작성 이유
     * -> 2명의 유저가 동시에 50번씩 사용 요청 했을경우 데이터 정합성 검증
     */
    @Test
    void 유저2명_포인트_사용_동시성_50번_성공() throws InterruptedException {
        // given
        final long user1 = 7L;
        final long amount = 20L;
        final long user1AmountInit = 2000L;
        final int threads = 100;

        final long user2 = 8L;
        final long amount2 = 30L;
        final long user2AmountInit = 3000L;

        pointService.chargeUserPoint(user1, user1AmountInit);
        pointService.chargeUserPoint(user2, user2AmountInit);

        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        // when
        for (int i = 0; i < threads; i++) {
            executorService.submit(() -> {
                try {
                    pointService.useUserPoint(user1, amount);
                    pointService.useUserPoint(user2, amount2);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        assertThat(pointService.findUserPointById(user1).point()).isEqualTo(ZERO);
        assertThat(pointService.findUserPointById(user2).point()).isEqualTo(ZERO);
    }
}
