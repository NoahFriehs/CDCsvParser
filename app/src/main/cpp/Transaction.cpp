//
// Created by nfriehs on 10/31/23.
//

#include "Transaction.h"

Transaction::Transaction() = default;

Transaction::~Transaction() = default;

Transaction::Transaction(int txID, int walletID, int fromWalletID, std::string description,
                         time_t txDate,
                         std::string currencyType, std::string toCurrencyType, double amount,
                         double toAmount,
                         double nativeAmount, double amountBonus, TransactionType txType,
                         std::string txTypeString,
                         std::string txHash, bool isOutsideTransaction, std::string notes) {
    this->txID = txID;
    this->walletID = walletID;
    this->fromWalletID = fromWalletID;
    this->description = std::move(description);
    this->txDate = txDate;
    this->currencyType = std::move(currencyType);
    this->toCurrencyType = std::move(toCurrencyType);
    this->amount = amount;
    this->toAmount = toAmount;
    this->nativeAmount = nativeAmount;
    this->amountBonus = amountBonus;
    this->txType = txType;
    this->txTypeString = std::move(txTypeString);
    this->txHash = std::move(txHash);
    this->isOutsideTransaction = isOutsideTransaction;
    this->notes = std::move(notes);
}
