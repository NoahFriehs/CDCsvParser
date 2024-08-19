package at.msd.friehs_bicha.cdcsvparser.util

class TimeSpan {
    private var startNanos: Long = 0

    fun start() {
        startNanos = System.nanoTime()
    }

    fun end(): Double {
        val endNanos = System.nanoTime()
        val elapsedNanos = endNanos - startNanos
        return elapsedNanos / 1_000_000.0 // Convert nanoseconds to milliseconds
    }
}
