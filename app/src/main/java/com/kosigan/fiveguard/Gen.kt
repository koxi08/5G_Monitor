package com.kosigan.fiveguard

import android.telephony.TelephonyManager

/** Cellular generation as the user sees it in the status bar. */
enum class Gen(val label: String) {
    NONE("—"), G2("2G"), G3("3G"), G4("4G"), G5("5G");

    companion object {
        // TelephonyDisplayInfo override types that Android shows as the "5G" icon (NSA / 5G+ / UC).
        private val NR_OVERRIDES = setOf(3, 4, 5)

        fun from(networkType: Int, overrideType: Int): Gen = when (networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> G5                      // 5G standalone
            TelephonyManager.NETWORK_TYPE_LTE ->
                if (overrideType in NR_OVERRIDES) G5 else G4          // LTE anchor + NR = 5G NSA
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN -> G2
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> G3
            else -> NONE
        }
    }
}
