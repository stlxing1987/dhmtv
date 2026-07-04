package com.github.catvod.utils;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Base64;

import com.github.catvod.Init;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Locale;

public class Util {

    public static byte[] decode(String s) {
        return Base64.decode(s, Base64.DEFAULT | Base64.NO_WRAP);
    }

    public static String getIp() {
        try {
            String ip = getHostAddress("wlan");
            if (!ip.isEmpty()) return ip;
            ip = getHostAddress("eth");
            if (!ip.isEmpty()) return ip;
            return getWifiAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private static String getWifiAddress() {
        Context context = Init.context();
        if (context == null) return "";
        WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (manager == null) return "";
        int ip = manager.getConnectionInfo().getIpAddress();
        return ip == 0 ? "" : String.format(Locale.getDefault(), "%d.%d.%d.%d", ip & 0xFF, (ip >> 8) & 0xFF, (ip >> 16) & 0xFF, (ip >> 24) & 0xFF);
    }

    private static String getHostAddress(String keyword) throws Exception {
        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
            NetworkInterface nif = en.nextElement();
            if (!keyword.isEmpty() && !nif.getName().startsWith(keyword)) continue;
            for (Enumeration<InetAddress> addresses = nif.getInetAddresses(); addresses.hasMoreElements(); ) {
                InetAddress addr = addresses.nextElement();
                if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                    return addr.getHostAddress();
                }
            }
        }
        return "";
    }
}
