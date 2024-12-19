# 동시성 제어 방식에 대한 분석 및 보고서 작성

### 포인트 정책 정의 
```
1. 포인트는 충전/차감 가능하다.
2. 유저마다 보유 포인트는 100만을 초과할 수 없다.
3. 포인트는 음수일 수 없다.
4. 보유한 포인트보다 많은 포인트를 사용할 수 없다.
```

### 발생할 수 있는 동시성 문제
```
1. 유저A가 사용/충전 요청을 보낸 후 바로 조회를 누를 경우 조회 요청이 더 빨리 온다면 변경사항이 반영되지 않는 조회를 하게된다.
2. 유저A가 사용/충전 요청을 동시에 여러번 보낼 경우 동시에 같은 데이터에 접근하여 같이 수정을 하므로 100을 2번 더해도 100이 나올 수 있다.(데이터 정합성 X)  
```

### 동시성 분석 및 제어 정책 정의
```
동시성 제어 정책
1. 동일한 유저에 대한 포인트 사용/충전/조회는 한번에 하나만 실행되어야 한다.
2. 동일한 유저에 대해 요청 순서가 보장되어야 한다.
   -> 첫번 째 100포인트 충전 요청, 두번 째 200포인트 충전 요청, 세번 째 300포인트 충전 요청 시 두번째의 합은 항상 300을 보장해야 한다.
```

### ConcurrentHashMap 과 ReentrantLock을 활용한 동시성 제어
```java
public class PointServiceImpl implements PointService {
    
    private final ConcurrentHashMap<String, ReentrantLock> lockConcurrentHashMap = new ConcurrentHashMap();
    private final String USER_LOCK_PREFIX = "USER_";
    
    /**
     * 공통 락 처리 유틸리티 메서드
     */
    private <T> T withLock(String key, Supplier<T> action) {
        ReentrantLock lock = lockConcurrentHashMap.computeIfAbsent(key, s -> new ReentrantLock(true));
        try {
            lock.lock();
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
```
- 사용 예시
```java
String key = USER_LOCK_PREFIX + userId;
return withLock(key, () -> {
    userIdValidation(userId);
    return userPointTable.selectById(userId);
});
```

### 기대하는 결과
```
1. 각 유저의 조회/사용/충전은 한번에 하나만 실행된다.
2. 각 유저의 충전/사용의 request 순서대로 처리되어야 한다. 
```

### 1번에 대한 테스트 검증
```
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
```
### 1번 테스트 결과
<!-- 결과 사진 -->

<br><br>
### 2번에 대한 테스트 검증을 위한 로그 추가 및 requestId 추가
```
@Override
public UserPoint chargeUserPoint(long userId, long amount, long requestId) {

    String key = USER_LOCK_PREFIX + userId;
    return withLock(key, () -> {
        log.info("requestId {} 락 획득",requestId); // Lock을 획득하는 순서 로그 출력
        userIdValidation(userId);
        amountValidation(amount);

        // 유저의 포인트 조회
        UserPoint currentUserPoint = userPointTable.selectById(userId);

        // 유저 포인트 합계
        long totalAmount = currentUserPoint.point() + amount;

        if (totalAmount > 1_000_000L) {
            throw new CustomException(ErrorMessageCode.TOO_MANY_AMOUNT_EXCEPTION);
        }

        // 히스토리에 충전 내역 저장
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());

        return userPointTable.insertOrUpdate(userId, totalAmount);
    });
}
```
### 2번 테스트 코드
```java
@Test
void 포인트_충전_동시성_순서_검증() throws InterruptedException {
    // given
    long userId = 3L;
    long amount = 500L;
    int threads = 100;

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    CountDownLatch latch = new CountDownLatch(threads);
    pointService.chargeUserPoint(userId,amount);

    // when
    for (int i = 0; i < threads; i++) {
        int requestId = i;
        executorService.submit(() -> {
            try {
                pointService.chargeUserPoint(userId, amount,requestId);
            } finally {
                latch.countDown();
            }
        });
    }
    latch.await();

    // then
    assertThat(pointService.findUserPointById(userId).point()).isEqualTo(amount * threads + amount);
}
```
- `newFixedThreadPool`는 멀티스레드 방식으로 동작 하기 때문에 `newSingleThreadExecutor` 로 변경
- `int requestId = i` 로 락을 획득하는 순서를 보기 위해 추가

### 2번 테스트 결과
<!-- 결과 사진 -->

<br><br>
### 결과
이번 동시성 제어 분석 및 테스트를 통해 유저의 포인트 충전 및 사용 기능에서 발생할 수 있는 주요 동시성 문제를 해결하고, 데이터 정합성을 유지할 수 있는 방안을 검증했습니다.

#### ConcurrentHashMap과 ReentrantLock의 활용
- 각 유저별로 고유한 락을 생성하여 충전, 사용, 조회 요청을 동기화하였습니다.
- 이를 통해 한 유저에 대한 요청이 동시에 처리되지 않도록 보장하였고, 데이터 정합성을 유지할 수 있음을 확인했습니다.
<br>
<br>
#### 테스트를 통한 신뢰성 검증
- 다수의 스레드 환경에서도 데이터의 일관성을 유지하며, 충전 및 사용 요청이 순차적으로 처리됨을 확인했습니다.
- newSingleThreadExecutor와 requestId를 활용하여 요청 처리 순서를 명확히 검증하였고, 원하는 순서대로 처리되는 것을 테스트 결과로 보장했습니다.

#### 앞으로의 개선 방향
- 현재 구현은 단일 서버에서 작동하기에 적합하며, 다중 서버 환경에서는 Redis와 같은 분산 락을 활용해 확장성을 고려해야 합니다.
- 요청 처리 및 락 상태를 실시간으로 모니터링할 수 있는 대시보드를 구축하면 문제 발생 시 더 빠르게 대응할 수 있습니다.