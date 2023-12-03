//
// Created by nfriehs on 11/5/23.
//

#ifndef NF_TX_CORE_IMP_H
#define NF_TX_CORE_IMP_H

#include <iostream>
#include <vector>
#include <map>
#include <stdexcept>

enum class AppType {
    CdCsvParser, CroCard, Default
};
enum class AppStatus {
    NotStarted, ImportFromFB, Finished
};
enum class DataTypes {
    csvAsList, dbWallets, dbOutsideWallets, dbTransactions, amountTxFailed
};

class BaseApp {
public:
    // Define BaseApp properties and methods
};

class StandardTxApp : public BaseApp {
public:
    StandardTxApp(const std::vector <std::string> &csvData, AppType appType) {
        // Implement constructor for StandardTxApp
    }
};

class CardTxApp : public BaseApp {
public:
    CardTxApp(const std::vector <std::string> &csvData, bool useStrictType) {
        // Implement constructor for CardTxApp
    }
};

class Wallet {
    // Define Wallet class
};

class Transaction {
    // Define Transaction class
};

class FileLog {
    // Define FileLog class
public:
    static void e(const std::string &tag, const std::string &message) {
        // Implement error logging
    }
};

class TxAppFactory {
public:
    static BaseApp *createTxApp(AppType appType, AppStatus appStatus, bool useStrictType,
                                const std::map<DataTypes, void *> &dataContainer) {
        BaseApp *txApp = nullptr;

        switch (appType) {
            case AppType::CdCsvParser:
                switch (appStatus) {
                    case AppStatus::NotStarted:
                        txApp = new StandardTxApp(
                                *static_cast<std::vector <std::string> *>(dataContainer.at(
                                        DataTypes::csvAsList)), appType);
                        break;
                    case AppStatus::ImportFromFB:
                        // Implement initFromFirebase
                        break;
                    case AppStatus::Finished:
                        // Implement initFromLocalDB
                        break;
                    default:
                        FileLog::e("TxAppFactory", "CdCsvParser: Usage not found, AppStatus: " +
                                                   std::to_string(static_cast<int>(appStatus)));
                        throw std::runtime_error("Usage not found");
                }
                break;
            case AppType::CroCard:
                switch (appStatus) {
                    case AppStatus::NotStarted:
                        txApp = new CardTxApp(
                                *static_cast<std::vector <std::string> *>(dataContainer.at(
                                        DataTypes::csvAsList)), useStrictType);
                        break;
                    case AppStatus::ImportFromFB:
                        // Implement processCroCardFromDB
                        break;
                    case AppStatus::Finished:
                        // Implement initCardFromLocalDB
                        break;
                    default:
                        FileLog::e("TxAppFactory", "CroCard: Usage not found, AppStatus: " +
                                                   std::to_string(static_cast<int>(appStatus)));
                        throw std::runtime_error("Usage not found");
                }
                break;
            case AppType::Default:
                switch (appStatus) {
                    case AppStatus::NotStarted:
                        txApp = new StandardTxApp(
                                *static_cast<std::vector <std::string> *>(dataContainer.at(
                                        DataTypes::csvAsList)), appType);
                        break;
                    default:
                        FileLog::e("TxAppFactory", "Default: Usage not found, AppStatus: " +
                                                   std::to_string(static_cast<int>(appStatus)));
                        throw std::runtime_error("Usage not found");
                }
                break;
            default:
                FileLog::e("TxAppFactory", "createTxApp: Usage not found, AppType: " +
                                           std::to_string(static_cast<int>(appType)));
                throw std::runtime_error("Usage not found");
        }

        return txApp;
    }
};

int main() {
    // Example usage of TxAppFactory
    std::map<DataTypes, void *> dataContainer;
    std::vector <std::string> csvData = {"data1", "data2", "data3"};
    dataContainer[DataTypes::csvAsList] = &csvData;

    BaseApp *txApp = TxAppFactory::createTxApp(AppType::CdCsvParser, AppStatus::NotStarted, true,
                                               dataContainer);

    // Use txApp as needed

    // Don't forget to clean up resources when done
    delete txApp;

    return 0;
}


#endif //NF_TX_CORE_IMP_H
