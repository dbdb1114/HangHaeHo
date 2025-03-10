package module.lock.functional;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import exception.BusinessError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionalDistributedLock {

	private static final String REDISSON_LOCK_PREFIX = "LOCK:";
	private static final Long LOCK_WAIT_TIME = 1L;
	private static final Long LOCK_LEASE_TIME = 2L;

	private final RedissonClient redissonClient;
	private final FunctionalForTransaction functionalForTransaction;

	public void executeLock(String keyPrefix, List<? extends Object> keys, Runnable runnable) {

		List<RLock> lockList = keys.stream()
			.sorted() // 교착상태 방지 ex: A : 1,2 / B : 2,1
			.map(key -> redissonClient.getLock(REDISSON_LOCK_PREFIX + keyPrefix + key))
			.toList();

		try{
			// 락 점유 하나라도 점유 실패 시 TryLockFailedException
			boolean allAvailable = acquireLock(lockList, LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

			// 배열내 모든 락이 점유 실패시
			// 점유한 락 모두 해제
			if(!allAvailable){
				leaseAllLock(lockList);
				throw BusinessError.RESERVATION_TRY_LOCK_FAILED.exception();
			}

			functionalForTransaction.run(runnable::run);
		} finally {
			// 메소드 종료 후 모든 락 점유 해제
			leaseAllLock(lockList);
		}
	}

	private void leaseAllLock(List<RLock> lockList) {
		for (RLock lock : lockList) {
			if (lock.isHeldByCurrentThread()) {
				try {
					lock.unlock();
				} catch (IllegalMonitorStateException e) {
					log.warn("Lock was already released: {}", lock.getName());
				}
			}
		}
	}

	private boolean acquireLock(List<RLock> lockList, Long waitTIme, Long leaseTime, TimeUnit timeUnit) {
		for (RLock lock : lockList) {
			try {
				if (!lock.tryLock(waitTIme, leaseTime, timeUnit)) {
					return false;
				}
			} catch (InterruptedException e) {
				return false;
			}
		}
		return true;
	}

}
