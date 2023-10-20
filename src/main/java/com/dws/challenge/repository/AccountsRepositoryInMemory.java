package com.dws.challenge.repository;

import com.dws.challenge.domain.Account;
import com.dws.challenge.exception.AccountLockException;
import com.dws.challenge.exception.DuplicateAccountIdException;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Repository
public class AccountsRepositoryInMemory implements AccountsRepository {

    private static final long DEFAULT_LOCK_TIMEOUT = 5000;

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public void createAccount(Account account) throws DuplicateAccountIdException {
        Account previousAccount = accounts.putIfAbsent(account.getAccountId(), account);
        if (previousAccount != null) {
            throw new DuplicateAccountIdException(
                    "Account id " + account.getAccountId() + " already exists!");
        }
    }

    @Override
    public Account getAccount(String accountId) {
        return accounts.get(accountId);
    }

    @Override
    public void clearAccounts() {
        accounts.clear();
    }

    /**
     * Acquire a lock for the specified account to ensure exclusive access.
     *
     * @param account The account for which the lock needs to be acquired.
     * @throws AccountLockException If the lock cannot be acquired
     */
    @Override
    public void lock(Account account) throws AccountLockException {
        var accountId = account.getAccountId();
        var lock = locks.computeIfAbsent(accountId, id -> new ReentrantLock());

        try {
            var locked = lock.tryLock(DEFAULT_LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!locked) {
                throw new AccountLockException(accountId);
            }
        } catch (InterruptedException e) {
            throw new AccountLockException(accountId);
        }
    }

    @Override
    public void unlock(Account account) {
        var lock = locks.get(account.getAccountId());
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

}
