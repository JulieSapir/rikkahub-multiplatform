package android.net.wifi

/**
 * mDNS 注册（web/NsdServiceRegistrar）在 Android 上要先把组播锁打开。桌面端网卡默认可用，
 * Context.getSystemService(WIFI_SERVICE) 返回 null，调用方是 `as? WifiManager`，所以这里只需要类型存在。
 */
class WifiInfo {
    val ipAddress: Int = 0
    val ssid: String? = null
}

class WifiManager {
    class MulticastLock(tag: String?) {
        val isHeld: Boolean = false

        fun setReferenceCounted(counted: Boolean) = Unit

        fun acquire() = Unit

        fun release() = Unit
    }

    fun createMulticastLock(tag: String?): MulticastLock = MulticastLock(tag)

    /** 桌面端拿不到 Wi-Fi 信息，ipAddress=0 让调用方回落到“本机 IP 取不到”。 */
    val connectionInfo: WifiInfo = WifiInfo()
}
