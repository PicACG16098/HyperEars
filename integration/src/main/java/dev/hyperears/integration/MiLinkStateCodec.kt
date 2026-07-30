package dev.hyperears.integration

object MiLinkStateCodec {
    fun batteryLevels(state: EarbudState): List<Int> = listOf(
        batteryPercent(state.battery.case),
        batteryPercent(state.battery.left),
        batteryPercent(state.battery.right),
        charging(state.battery.case),
        charging(state.battery.left),
        charging(state.battery.right),
    )

    fun regularBatteryLevel(state: EarbudState): Int {
        val availableEars = listOf(state.battery.left, state.battery.right)
            .mapNotNull(BatteryReading::percent)
        return availableEars.minOrNull() ?: -1
    }

    fun ancState(state: EarbudState): Int = when (state.noiseMode) {
        NoiseMode.ANC -> 1
        NoiseMode.TRANSPARENCY -> 2
        NoiseMode.OFF, null -> 0
    }

    private fun batteryPercent(reading: BatteryReading): Int = reading.percent ?: -1

    private fun charging(reading: BatteryReading): Int =
        if (reading.available && reading.charging) 1 else 0
}
