package at.msd.friehs_bicha.cdcsvparser.util

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog

class Benchmarker {
    companion object {
        private var startTime: Long = 0
        private var endTime: Long = 0

        fun start() {
            startTime = System.currentTimeMillis()
        }

        fun stop() {
            endTime = System.currentTimeMillis()
            FileLog.i("Benchmarker", "Duration: " + duration() + "ms")
            FileLog.i("Benchmarker", "Duration: " + durationInSeconds() + "s")
        }

        fun duration(): Long {
            return endTime - startTime
        }

        fun durationInSeconds(): Double {
            return (endTime - startTime) / 1000.0
        }
    }
}