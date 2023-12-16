#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wreturn-type-c-linkage"  // Ignore warning about C linkage
#ifndef NF_TX_CORE_LIBRARY_H
#define NF_TX_CORE_LIBRARY_H

#include <vector>
#include <string>

#ifdef __cplusplus
extern "C" {
#endif


bool init();

bool initWithData(const std::vector<std::string> &data, uint mode);

void save();

void loadData();

void calculateBalances();

std::vector<std::string> getCurrencies();

void setPrice(std::vector<double> prices);

double getTotalMoneySpent();

double getTotalMoneySpentCard();

double getTotalValueOfAssets();

double getTotalValueOfAssetsCard();

double getTotalBonus();

double getTotalBonusCard();

double getValueOfAssets(int walletId);

double getBonus(int walletId);

double getMoneySpent(int walletId);

std::vector<std::string> getWalletsAsStrings();

std::vector<std::string> getCardWalletsAsStrings();

std::string getWalletAsString(int walletId);

std::vector<std::string> getTransactionsAsStrings();

std::vector<std::string> getCardTransactionsAsStrings();

#ifdef __cplusplus
}
#endif

#endif //NF_TX_CORE_LIBRARY_H

#pragma clang diagnostic pop