package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.dto.TransferOperation;
import com.dws.challenge.exception.AccountLockException;
import com.dws.challenge.exception.InsufficientAmountException;
import com.dws.challenge.exception.NotExistedAccountException;
import com.dws.challenge.repository.AccountsRepository;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AccountsService {

    private final static String TRANSFERRED_FROM_MESSAGE = "%s is transferred from %s";
    private final static String TRANSFERRED_TO_MESSAGE = "%s is transferred to %s";

    @Getter
    private final AccountsRepository accountsRepository;
    private final NotificationService notificationService;

    @Autowired
    public AccountsService(AccountsRepository accountsRepository, NotificationService notificationService) {
        this.accountsRepository = accountsRepository;
        this.notificationService = notificationService;
    }

    public void createAccount(Account account) {
        this.accountsRepository.createAccount(account);
    }

    public Account getAccount(String accountId) {
        return this.accountsRepository.getAccount(accountId);
    }

    /**
     * Transfer money from one account to another.
     *
     * @param transferOperation The transfer operation containing source and destination account IDs and the amount to transfer.
     * @throws InsufficientAmountException If the source account has an insufficient balance.
     * @throws AccountLockException If there's an issue acquiring locks for the source and destination accounts.
     */
    public void transfer(TransferOperation transferOperation) throws InsufficientAmountException, AccountLockException {
        var from = getAccountOrThrow(transferOperation.getAccountFromId());
        var to = getAccountOrThrow(transferOperation.getAccountToId());

        try {
            accountsRepository.lock(from);
            accountsRepository.lock(to);

            var amount = transferOperation.getAmount();
            from.withdraw(amount);
            to.deposit(amount);

            notificationService.notifyAboutTransfer(from,
                    String.format(TRANSFERRED_TO_MESSAGE, amount, to.getAccountId()));
            notificationService.notifyAboutTransfer(to,
                    String.format(TRANSFERRED_FROM_MESSAGE, amount, from.getAccountId()));
        } finally {
            accountsRepository.unlock(from);
            accountsRepository.unlock(to);
        }
    }

    private Account getAccountOrThrow(String accountId) throws NotExistedAccountException {
        var account = accountsRepository.getAccount(accountId);
        if (account == null) {
            throw new NotExistedAccountException(accountId);
        }
        return account;
    }
}
