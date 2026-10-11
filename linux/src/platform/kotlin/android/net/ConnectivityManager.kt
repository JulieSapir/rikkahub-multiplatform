package android.net

/**
 * 终端页用它判断“当前网络是不是计费网络”以提示流量，桌面端不适用：
 * 一律报“没有活动网络”，上游逻辑据此跳过提示。
 */
class ConnectivityManager {
    val activeNetwork: Network? = null

    fun getNetworkCapabilities(network: Network?): NetworkCapabilities? = null

    fun getLinkProperties(network: Network?): LinkProperties? = null

    fun isActiveNetworkMetered(): Boolean = false
}

class Network(val type: Int = 0)

class NetworkCapabilities {
    fun hasCapability(capability: Int): Boolean = false

    companion object {
        const val NET_CAPABILITY_NOT_METERED = 11
        const val NET_CAPABILITY_INTERNET = 12
        const val NET_CAPABILITY_NOT_ROAMING = 18
    }
}

class LinkProperties {
    val linkAddresses: List<String> = emptyList()
}
