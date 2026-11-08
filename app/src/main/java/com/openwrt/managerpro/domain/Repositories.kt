package com.openwrt.managerpro.domain

interface RouterRepository {
    suspend fun systemBoard(): SystemBoard
    suspend fun systemInfo(): SystemInfo
    suspend fun networkDump(): List<NetInterface>
    suspend fun temperatureC(): Int?
    suspend fun testConnection(profile: RouterProfile): TestResult
    suspend fun hasProfile(): Boolean
    suspend fun clearProfile()
}

interface DeviceRepository {
    suspend fun devices(): List<DeviceRecord>
    suspend fun devicesOnline(): Int
    suspend fun knownMacs(): Set<String>
    suspend fun updateKnownMacs(macs: Set<String>)
    suspend fun recordSeen(macs: Set<String>)
}

interface FirmwareRepository {
    suspend fun checkUpdate(): FirmwareStatus
}
