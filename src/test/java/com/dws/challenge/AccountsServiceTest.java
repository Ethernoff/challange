package com.dws.challenge;

import com.dws.challenge.domain.Account;
import com.dws.challenge.dto.TransferOperation;
import com.dws.challenge.exception.DuplicateAccountIdException;
import com.dws.challenge.exception.InsufficientAmountException;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class AccountsServiceTest {

    @Autowired
    private AccountsService accountsService;

    @MockBean
    private NotificationService notificationService;

    @Test
    void addAccount() {
        Account account = new Account("Id-123");
        account.setBalance(new BigDecimal(1000));
        this.accountsService.createAccount(account);

        assertThat(this.accountsService.getAccount("Id-123")).isEqualTo(account);
    }

    @Test
    void addAccount_failsOnDuplicateId() {
        String uniqueId = "Id-" + System.currentTimeMillis();
        Account account = new Account(uniqueId);
        this.accountsService.createAccount(account);

        try {
            this.accountsService.createAccount(account);
            fail("Should have failed when adding duplicate account");
        } catch (DuplicateAccountIdException ex) {
            assertThat(ex.getMessage()).isEqualTo("Account id " + uniqueId + " already exists!");
        }
    }

    @Test
    void transfer() {
        var initialBalanceFrom = new BigDecimal(100);
        var initialBalanceTo = new BigDecimal(50);
        var from = createAccount(initialBalanceFrom);
        var to = createAccount(initialBalanceTo);
        var operation = new TransferOperation(from.getAccountId(), to.getAccountId(), new BigDecimal(50));

        accountsService.transfer(operation);

        var expectedBalanceFrom = initialBalanceFrom.subtract(operation.getAmount());
        var expectedBalanceTo = initialBalanceTo.add(operation.getAmount());

        assertEquals(expectedBalanceFrom, from.getBalance());
        assertEquals(expectedBalanceTo, to.getBalance());
    }

    @Test
    void notificationIsSentAfterTransfer() {
        var from = createAccount(new BigDecimal(100));
        var to = createAccount(new BigDecimal(50));
        var operation = new TransferOperation(from.getAccountId(), to.getAccountId(), new BigDecimal(50));

        accountsService.transfer(operation);

        verify(notificationService).notifyAboutTransfer(Mockito.same(from), Mockito.anyString());
        verify(notificationService).notifyAboutTransfer(Mockito.same(to), Mockito.anyString());
    }

    @Test
    void insufficientBalance() {
        var initialBalanceFrom = new BigDecimal(1);
        var initialBalanceTo = new BigDecimal(50);
        var from = createAccount(initialBalanceFrom);
        var to = createAccount(initialBalanceTo);
        var operation = new TransferOperation(from.getAccountId(), to.getAccountId(), new BigDecimal(50));

        assertThrows(InsufficientAmountException.class, () -> accountsService.transfer(operation));
    }

    @Test
    void concurrentTransfer() throws InterruptedException {
        var initialBalanceFrom = new BigDecimal(100);
        var initialBalanceTo = new BigDecimal(50);
        var from = createAccount(initialBalanceFrom);
        var to = createAccount(initialBalanceTo);
        var operation = new TransferOperation(from.getAccountId(), to.getAccountId(), new BigDecimal(1));
        var operationCount = 50;

        //long is used to simplify testing
        var expectedBalanceFrom = new AtomicLong(initialBalanceFrom.longValue());
        var expectedBalanceTo = new AtomicLong(initialBalanceTo.longValue());

        var threadPool = Executors.newFixedThreadPool(2);
        for (int i = 0; i < operationCount; i++) {
            threadPool.submit(() -> transfer(operation, expectedBalanceFrom, expectedBalanceTo));
        }
        threadPool.shutdown();
        threadPool.awaitTermination(1, TimeUnit.MINUTES);

        assertEquals(expectedBalanceFrom.get(), from.getBalance().longValue());
        assertEquals(expectedBalanceTo.get(), to.getBalance().longValue());
    }

    private void transfer(TransferOperation operation, AtomicLong fromBalance, AtomicLong toBalance) {
        accountsService.transfer(operation);
        fromBalance.addAndGet(-operation.getAmount().longValue());
        toBalance.addAndGet(operation.getAmount().longValue());
    }

    @Test
    void multipleTransferBetweenTwoAccounts() throws InterruptedException {
        var initialBalanceFrom = new BigDecimal(100);
        var initialBalanceTo = new BigDecimal(50);
        var from = createAccount(initialBalanceFrom);
        var to = createAccount(initialBalanceTo);
        var operationFromTo = new TransferOperation(from.getAccountId(), to.getAccountId(), new BigDecimal(1));
        var operationToFrom = new TransferOperation(to.getAccountId(), from.getAccountId(), new BigDecimal(1));
        var operationCount = 10;

        var expectedBalanceFrom = new AtomicLong(initialBalanceFrom.longValue());
        var expectedBalanceTo = new AtomicLong(initialBalanceTo.longValue());

        var threadPool = Executors.newFixedThreadPool(2);
        for (int i = 0; i < operationCount; i++) {
            threadPool.submit(() -> transfer(operationFromTo, expectedBalanceFrom, expectedBalanceTo));
            threadPool.submit(() -> transfer(operationToFrom, expectedBalanceTo, expectedBalanceFrom));
        }
        threadPool.shutdown();
        threadPool.awaitTermination(1, TimeUnit.MINUTES);

        assertEquals(expectedBalanceFrom.get(), from.getBalance().longValue());
        assertEquals(expectedBalanceTo.get(), to.getBalance().longValue());
    }

    private Account createAccount(BigDecimal balance) {
        String uniqueId = UUID.randomUUID().toString();
        Account account = new Account(uniqueId);
        account.setBalance(balance);
        this.accountsService.createAccount(account);
        return account;
    }
}
