package io.hhplus.tdd.point;

import io.hhplus.tdd.CustomException;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static io.hhplus.tdd.CustomException.ErrorMessageCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 포인트 서비스 단위 테스트
 *
 * - 포인트 정책 정리/정의
 *     1. 포인트는 충전/차감 가능하다.
 *     2. 보유 포인트는 100만을 초과할 수 없다.
 *     3. 포인트는 음수일 수 없다.
 *     4. 포인트가 0일경우 포인트를 사용할 수 없다.
 *
 * - 서비스 유효성 정의
 *     1. 매개변수 유저 아이디는 음수일 수 없다.
 *     2. 충전/차감하려는 포인트는 음수일 수 없다.
 *     3. 기존 포인트와 충전하려는 포인트의 합이 100만을 초과할 수 없다.
 *     4. 기존 포인트보다 많은 포인트를 사용할 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class PointServiceUnitTest {

    @InjectMocks
    private PointServiceImpl pointService;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @Mock
    private UserPointTable userPointTable;

    /**
     * 테스트 케이스 작성 이유
     * -> ID를 음수로 보냈을 경우에 대한 유효성 검사가 제대로 동작하는지 검증
     */
    @Test
    void 유저아이디를_음수로보내면_커스텀에러발생_실패() {
        // given
        long userId = -1L;

        //when
        try {
            pointService.findUserPointById(userId);
            fail();
        } catch (CustomException e) {
            //then
            assertThat(e.getMessage()).isEqualTo("유저 아이디는 음수일 수 없습니다.");
            verify(userPointTable, times(0)).selectById(userId);
        }
    }

    /**
     * 테스트 케이스 작성 이유
     * -> 정상적인 양수의 ID를 보냈을 경우 Repository 호출이 제대로 작동하는지 검증
     */
    @Test
    void 유저포인트조회_성공() {
        // given
        long userId = 1L;
        UserPoint emptyUserPoint = UserPoint.empty(userId);
        when(userPointTable.selectById(userId)).thenReturn(emptyUserPoint);

        //when
        UserPoint result = pointService.findUserPointById(userId);

        //then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        verify(userPointTable, times(1)).selectById(userId);
    }

    /**
     * 테스트 케이스 작성 이유
     * -> ID를 음수로 보냈을 경우에 대한 유효성 검사가 제대로 동작하는지 검증
     */
    @Test
    void 유저아이디를_음수로_보내면_포인트_이용내역_조회_실패() {
        // given
        long userId = -1L;

        //when
        try {
            pointService.findPointHistoriesById(userId);
            fail();
        } catch (CustomException e) {
            //then
            assertThat(e.getMessage()).isEqualTo("유저 아이디는 음수일 수 없습니다.");
            verify(pointHistoryTable, times(0)).selectAllByUserId(userId);
        }
    }

    /**
     * 테스트 케이스 작성 이유
     * -> 정상적인 양수의 ID를 보냈을 경우 Repository 호출이 제대로 작동하는지 검증
     */
    @Test
    void 특정유저의_포인트_이용내역_조회_성공() {
        // given
        long userId = 1L;
        long pointHistoryId = 1L;
        long amount = 2000l;
        TransactionType charge = TransactionType.CHARGE;
        long updateMillis = System.currentTimeMillis();

        List<PointHistory> pointHistories = List.of(
                new PointHistory(pointHistoryId, userId, amount, charge, updateMillis)
        );

        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(pointHistories);

        //when
        List<PointHistory> result = pointService.findPointHistoriesById(userId);

        //then
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0).amount()).isEqualTo(amount);
        verify(pointHistoryTable, times(1)).selectAllByUserId(userId);
    }

    /**
     * 테스트 케이스 작성 이유
     * -> 기존 포인트에 입력받은 포인트를 충전하려 시도할 경우 100만을 초과한다면 커스텀 에러 반환 검증
     * -> 비즈니스 에러 반환 전까지의 repository 호출은 정상적으로 이루어졌는지 검증
     */
    @Test
    void 보유포인트는_100만초과_커스텀에러메시지_실패() {
        // given
        long userId = 1L;
        long point = 900_000L;
        long amount = 100_001L;
        long pointHistoryId = 1L;
        TransactionType transactionType = TransactionType.CHARGE;
        long updateMillis = System.currentTimeMillis();

        when(userPointTable.selectById(userId))
                .thenReturn(new UserPoint(userId, point,updateMillis));

        when(pointHistoryTable.insert(eq(userId), eq(amount), eq(transactionType), any(long.class)))
                .thenReturn(new PointHistory(pointHistoryId, userId, amount, transactionType, updateMillis));

        //when
        try {
            pointService.chargeUserPoint(userId, amount);
            fail();
        } catch (CustomException e) {
            //then
            assertThat(e.getMessage()).isEqualTo("포인트는 100만을 초과할 수 없습니다.");
            verify(userPointTable, times(1)).selectById(userId);
            verify(pointHistoryTable, times(1)).insert(eq(userId), eq(amount), eq(transactionType), any(long.class));
        }

    }

    /**
     * 테스트 케이스 작성 이유
     * -> 음수 포인트를 충전 시도 시 서비스의 AMOUNT 유효성검사 동작 검증
     */
    @Test
    void 포인트_음수_충전_유효하지않은_AMOUNT_에러_실패() {
        // given
        long userId = 1L;
        long amount = -2000L;

        //when
        try {
            pointService.chargeUserPoint(userId, amount);
            fail();
        } catch (CustomException e) {
            //then
            assertThat(e.getMessage()).isEqualTo("AMOUNT가 음수일 수 없습니다.");
        }
    }

    /**
     * 테스트 케이스 작성 이유
     * -> 서비스에서 각각의 호출이 정상적으로 이루어지는지 검증
     * -> 입력받은 포인트와 가지고 있는 포인트를 합치는 비즈니스 로직이 정상으로 동작하는지 검증
     */
    @Test
    void 포인트_2_000_충전_성공() {
        // given
        long userId = 1L;
        long amount = 2000L;
        long pointHistoryId = 1L;
        long totalPoint = 4000L;
        TransactionType transactionType = TransactionType.CHARGE;
        long updateMillis = System.currentTimeMillis();

        when(userPointTable.selectById(userId))
                .thenReturn(new UserPoint(userId,amount,updateMillis));

        when(pointHistoryTable.insert(eq(userId), eq(amount), eq(transactionType), any(long.class)))
                .thenReturn(new PointHistory(pointHistoryId, userId, amount, transactionType, updateMillis));

        // 충전 후 유저 포인트
        when(userPointTable.insertOrUpdate(userId, totalPoint))
                .thenReturn(new UserPoint(userId, totalPoint, updateMillis));

        //when
        UserPoint result = pointService.chargeUserPoint(userId, amount);

        //then
        assertThat(result.point()).isEqualTo(totalPoint);
        verify(userPointTable, times(1)).selectById(userId);
        verify(pointHistoryTable, times(1)).insert(eq(userId), eq(amount), eq(transactionType), any(long.class));
        verify(userPointTable, times(1)).insertOrUpdate(userId, totalPoint);
    }

    /**
     * 테스트 케이스 작성 이유
     * -> 현재 보유중인 유저의 포인트보다 많은 포인트를 사용하려 시도 할 경우 실패 검증
     */
    @Test
    void 보유포인트보다_많은포인트_사용_커스텀에러메시지_실패() {
        // given
        long userId = 1L;
        long point = 3000L;
        long amount = 4000L;
        long updateMillis = System.currentTimeMillis();
        UserPoint chargeUserPoint = new UserPoint(userId, point, updateMillis);
        when(userPointTable.selectById(userId)).thenReturn(chargeUserPoint);

        //when
        try {
            pointService.useUserPoint(userId, amount);
            fail();
        } catch (CustomException e) {
            //then
            assertThat(e.getMessage()).isEqualTo("보유 포인트가 부족합니다.");
            verify(userPointTable, times(1)).selectById(userId);
        }

    }

    /**
     * 테스트 케이스 작성 이유
     * -> 올바른 값을 보냈을 경우(보유 포인트10 사용 포인트 10) 제대로 동작하는지에 대한 검증
     * -> 보유 포인트를 모두 사용하여 남은 포인트가 0인지에 대한 검증
     */
    @Test
    void 포인트_사용_성공() {
        // given
        long userId = 1L;
        long amount = 10L;
        TransactionType transactionType = TransactionType.USE;
        long updateMillis = System.currentTimeMillis();
        long pointHistoryId = 1L;
        UserPoint chargeUserPoint = new UserPoint(userId, amount, updateMillis);
        long afterAmount = chargeUserPoint.point() - amount;

        when(userPointTable.selectById(userId))
                .thenReturn(chargeUserPoint);
        when(pointHistoryTable.insert(eq(userId), eq(amount), eq(transactionType), any(long.class)))
                .thenReturn(new PointHistory(pointHistoryId, userId, amount, transactionType, updateMillis));
        when(userPointTable.insertOrUpdate(userId, afterAmount))
                .thenReturn(UserPoint.empty(userId));

        //when
        UserPoint result = pointService.useUserPoint(userId, amount);

        //then
        assertThat(result.point()).isEqualTo(0L);
        verify(userPointTable, times(1)).selectById(userId);
        verify(pointHistoryTable, times(1)).insert(eq(userId), eq(amount), eq(transactionType), any(long.class));
        verify(userPointTable, times(1)).insertOrUpdate(userId, afterAmount);
    }
}