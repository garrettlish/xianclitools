package com.example.xiancli_tools.data.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build

/**
 * 同一 Wi-Fi 下的设备发现：用系统自带的 mDNS（[NsdManager]）广播自己、搜索别人。
 *
 * 选它而不是自己发 UDP 广播，是因为它不需要额外权限、路由器普遍放行，
 * 也不用自己处理「谁在哪个网段」这种琐事。
 *
 * 一个坑：`resolveService` 同一时刻只能有一个在跑，并发调用会直接回调
 * `FAILURE_ALREADY_ACTIVE`，所以这里用队列串行化。
 */
class LanDiscovery(context: Context) {

    /** 解析出的对端：设备名 + 可直连的地址。 */
    data class Peer(val name: String, val host: String, val port: Int)

    private val manager: NsdManager? =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null
    private var selfName: String? = null
    private var selfPort: Int = 0

    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    val isAvailable: Boolean
        get() = manager != null

    /** 广播本机服务。[onRegistered] 在系统确认注册成功后回调，可以拿到稳定的服务名。 */
    fun advertise(
        port: Int,
        deviceName: String,
        onRegistered: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val nsd = manager ?: run {
            onError("这台设备不支持局域网发现")
            return
        }
        stopAdvertising()

        val info = NsdServiceInfo().apply {
            serviceName = deviceName.ifBlank { "朝夕" }
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        selfPort = port
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                selfName = info.serviceName
                onRegistered(info.serviceName)
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) =
                onError("广播失败（错误码 $errorCode）")

            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { onError(it.message ?: "广播失败") }
    }

    fun browse(
        onFound: (Peer) -> Unit,
        onLost: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val nsd = manager ?: run {
            onError("这台设备不支持局域网发现")
            return
        }
        stopBrowsing()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) =
                onError("搜索失败（错误码 $errorCode）")

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                // 有的系统会把服务类型的尾点去掉，两边都按无尾点比较，否则整片搜不到。
                if (info.serviceType.trimEnd('.') != SERVICE_TYPE.trimEnd('.')) return
                // 自己也会被搜到。注册回调是异步的，名字可能还没拿到，所以端口也要比一下。
                if (info.serviceName == selfName || (selfPort != 0 && info.port == selfPort)) return
                resolve(info, onFound)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                onLost(info.serviceName)
            }
        }
        discoveryListener = listener
        runCatching {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { onError(it.message ?: "搜索失败") }
    }

    fun stop() {
        stopBrowsing()
        stopAdvertising()
        resolveQueue.clear()
        resolving = false
    }

    private fun resolve(info: NsdServiceInfo, onFound: (Peer) -> Unit) {
        if (resolveQueue.any { it.serviceName == info.serviceName }) return
        resolveQueue.addLast(info)
        drainResolveQueue(onFound)
    }

    private fun drainResolveQueue(onFound: (Peer) -> Unit) {
        if (resolving) return
        val nsd = manager ?: return
        val info = resolveQueue.removeFirstOrNull() ?: return
        resolving = true

        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                resolving = false
                resolveQueue.removeAll { it.serviceName == serviceInfo.serviceName }
                drainResolveQueue(onFound)
            }

            @Suppress("DEPRECATION")
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                resolving = false
                val host = if (Build.VERSION.SDK_INT >= 34) {
                    serviceInfo.hostAddresses?.firstOrNull()?.hostAddress
                } else {
                    serviceInfo.host?.hostAddress
                }
                if (host != null && serviceInfo.port > 0) {
                    onFound(Peer(serviceInfo.serviceName, host, serviceInfo.port))
                }
                drainResolveQueue(onFound)
            }
        }
        resolveListener = listener
        // resolveService 在 API 34 起被标记为废弃，但替代的 registerServiceInfoCallback
        // 要 API 34+ 且回调模型不同；minSdk 24 下继续用这个，行为没问题。
        @Suppress("DEPRECATION")
        runCatching { nsd.resolveService(info, listener) }.onFailure {
            resolving = false
            drainResolveQueue(onFound)
        }
    }

    private fun stopAdvertising() {
        registrationListener?.let { runCatching { manager?.unregisterService(it) } }
        registrationListener = null
        selfName = null
        selfPort = 0
    }

    private fun stopBrowsing() {
        discoveryListener?.let { runCatching { manager?.stopServiceDiscovery(it) } }
        discoveryListener = null
        resolveListener = null
    }

    private companion object {
        const val SERVICE_TYPE = "_xiancli._tcp."
    }
}
