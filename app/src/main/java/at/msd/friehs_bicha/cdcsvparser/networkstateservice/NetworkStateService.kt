package at.msd.friehs_bicha.cdcsvparser.networkstateservice

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.IBinder
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog

class NetworkStateService : Service() {

    private var isConnected: Boolean = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // The network is now available.
            isConnected = true
            notifyApp()
        }

        override fun onLost(network: Network) {
            // The network is no longer available.
            isConnected = false
            notifyApp()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        // Register the network callback listener.
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)
        notifyApp()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Unregister the network callback listener.
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private fun notifyApp() {
        // Send a broadcast intent to notify the app of the network state change.
        //val intent = Intent(ACTION_NETWORK_STATE_CHANGED)
        //intent.putExtra(EXTRA_IS_CONNECTED, isConnected)
        //sendBroadcast(intent)
        //AssetValue.getInstance().isConnected = isConnected
        FileLog.d("NetworkStateService", "Network state changed, isConnected: $isConnected")
    }

    companion object {
        const val ACTION_NETWORK_STATE_CHANGED = "at.msd.friehs_bicha.cdcsvparser.networkstateservice.action.NETWORK_STATE_CHANGED"
        const val EXTRA_IS_CONNECTED = "at.msd.friehs_bicha.cdcsvparser.networkstateservice.extra.IS_CONNECTED"
    }
}
