
cmake_minimum_required(VERSION 3.22.1)

project("cdcsvparser")

# Align all ELF PT_LOAD segments to 16KB so the native library loads on
# 16KB page-size devices (Android 15+). Also works on 4KB page-size devices.
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

add_library(${CMAKE_PROJECT_NAME} SHARED
        FileLog.h
        library.h
        library.cpp
        Wallet/Wallet.cpp
        Util/Util.h
        DataHolder.cpp
        DataHolder.h
        TransactionParser.cpp
        TransactionParser.h
        TransactionManager.cpp
        TransactionManager.h
        Transaction/BaseTransaction.cpp
        Transaction/BaseTransaction.h
        Enums.h
        FileLog.cpp
        TimeSpan.h
        Wallet/WalletBalance.h
        Price/PriceCache.cpp
        Price/PriceCache.h
        Price/Cache.cpp
        Price/Cache.h
        Price/AssetValue.cpp
        Price/AssetValue.h
        Price/StaticPrices.h
        Structs.h
        BinaryUtil.h
        Transaction/TransactionStruct.h
        Wallet/WalletStruct.h
        TransactionManager/TMState.cpp
        TransactionManager/TMState.h
        Util/CharUtil.h
        Util/Util.cpp
        Util/CharUtil.cpp
        MagicNumbers.h)


include_directories(XML)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log)
