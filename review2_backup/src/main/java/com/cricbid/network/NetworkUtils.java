package com.cricbid.network;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public final class NetworkUtils {

    private NetworkUtils() {}

    /**
     * Detects the primary LAN / Wi-Fi / Mobile Hotspot IPv4 address of this machine.
     */
    public static String getLocalLanIp() {
        String fallbackIp = null;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
                    continue;
                }
                String name = iface.getName().toLowerCase();
                if (name.startsWith("utun") || name.startsWith("awdl") || name.startsWith("llw")
                        || name.startsWith("bridge") || name.startsWith("docker") || name.startsWith("veth")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (name.startsWith("en") || name.startsWith("wlan") || name.startsWith("wi")) {
                            return ip;
                        }
                        if (fallbackIp == null && addr.isSiteLocalAddress()) {
                            fallbackIp = ip;
                        }
                    }
                }
            }
            if (fallbackIp != null) {
                return fallbackIp;
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
            return "localhost";
        }
    }
}
