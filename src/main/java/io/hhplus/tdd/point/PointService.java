package io.hhplus.tdd.point;

import java.util.List;

public interface PointService {
    UserPoint findUserPointById(long userId);

    List<PointHistory> findPointHistoriesById(long userId);

    UserPoint chargeUserPoint(long userId, long amount);

    UserPoint useUserPoint(long userId, long amount);
}
