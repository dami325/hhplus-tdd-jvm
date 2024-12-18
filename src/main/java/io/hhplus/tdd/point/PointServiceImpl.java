package io.hhplus.tdd.point;

import io.hhplus.tdd.CustomException;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static io.hhplus.tdd.CustomException.ErrorMessageCode;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointServiceImpl implements PointService {

    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;

    @Override
    public UserPoint findUserPointById(long userId) {
        userIdValidation(userId);
        return userPointTable.selectById(userId);
    }

    @Override
    public List<PointHistory> findPointHistoriesById(long userId) {
        userIdValidation(userId);
        return pointHistoryTable.selectAllByUserId(userId);
    }

    @Override
    public UserPoint chargeUserPoint(long userId, long amount) {

        userIdValidation(userId);
        amountValidation(amount);

        // 유저의 포인트 조회
        UserPoint currentUserPoint = userPointTable.selectById(userId);

        // 유저 포인트 합계
        long totalAmount = currentUserPoint.point() + amount;

        if(totalAmount > 1_000_000L){
            throw new CustomException(ErrorMessageCode.TOO_MANY_AMOUNT_EXCEPTION);
        }

        // 히스토리에 충전 내역 저장
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());

        return userPointTable.insertOrUpdate(userId, totalAmount);
    }


    @Override
    public UserPoint useUserPoint(long userId, long amount) {

        userIdValidation(userId);
        amountValidation(amount);

        // 유저의 포인트 조회
        UserPoint currentUserPoint = userPointTable.selectById(userId);

        long currentPoint = currentUserPoint.point();

        // 현재 포인트가 입력받은 포인트 보다 적을 경우 사용할 수 없음
        if (currentPoint < amount) {
            throw new CustomException(ErrorMessageCode.NOT_ENOUGH_POINTS);
        }

        // 히스토리에 차감 내역 저장
        pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());

        // 현재 포인트에서 입력받은 포인트 차감
        long totalAmount = currentPoint - amount;

        return userPointTable.insertOrUpdate(userId, totalAmount);
    }

    private void amountValidation(long amount) {
        if (amount <= 0) {
            throw new CustomException(ErrorMessageCode.AMOUNT_EXCEPTION);
        }
    }

    private void userIdValidation(long userId) {
        if (userId <= 0) {
            throw new CustomException(ErrorMessageCode.USER_ID_EXCEPTION);
        }
    }
}
