//
// Created by nfriehs on 10/31/23.
//

#ifndef NF_TX_CORE_TRANSACTION_H
#define NF_TX_CORE_TRANSACTION_H


#include <string>
#include "Util.h"

class Transaction {
public:
    Transaction();

    Transaction(int txID, int walletID, int fromWalletID, std::string description, time_t txDate,
                std::string currencyType, std::string toCurrencyType, double amount,
                double toAmount, double nativeAmount, double amountBonus, TransactionType txType,
                std::string txTypeString, std::string txHash, bool isOutsideTransaction,
                std::string notes);

    ~Transaction();

private:
    int txID{};
    int walletID{};
    int fromWalletID{};
    std::string description;
    time_t txDate{};
    std::string currencyType;
    std::string toCurrencyType;
    double amount{};
    double toAmount{};
    double nativeAmount{};
    double amountBonus{};
    TransactionType txType = STRING;
    std::string txTypeString;
    std::string txHash;
    bool isOutsideTransaction = false;
    std::string notes;

};


#endif //NF_TX_CORE_TRANSACTION_H
