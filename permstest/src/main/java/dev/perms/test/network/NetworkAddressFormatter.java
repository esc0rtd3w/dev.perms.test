package dev.perms.test.network;

import android.text.TextUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public final class NetworkAddressFormatter {
    public static final class Status {
        public final String text;
        public final boolean connected;
        public final String firstIpv4;

        Status(String text, boolean connected, String firstIpv4) {
            this.text = text;
            this.connected = connected;
            this.firstIpv4 = firstIpv4;
        }
    }

    private NetworkAddressFormatter() {
    }

    public static Status currentStatus() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            Collections.sort(interfaces, (a, b) -> score(b) - score(a));
            for (NetworkInterface nif : interfaces) {
                if (!isUsable(nif)) continue;

                String ipv4 = null;
                String ipv6 = null;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr == null || addr.isLoopbackAddress()) continue;
                    if (addr instanceof Inet4Address) {
                        if (ipv4 == null) ipv4 = addr.getHostAddress();
                    } else if (addr instanceof Inet6Address) {
                        String host = addr.getHostAddress();
                        if (host != null) {
                            int zone = host.indexOf('%');
                            if (zone >= 0) host = host.substring(0, zone);
                        }
                        if (!TextUtils.isEmpty(host) && ipv6 == null) ipv6 = host;
                    }
                }

                if (!TextUtils.isEmpty(ipv4) || !TextUtils.isEmpty(ipv6)) {
                    ArrayList<String> parts = new ArrayList<>();
                    if (!TextUtils.isEmpty(ipv4)) parts.add("IPv4 " + ipv4);
                    if (!TextUtils.isEmpty(ipv6)) parts.add("IPv6 " + shortenIpv6(ipv6));
                    return new Status(nif.getName() + "  " + TextUtils.join("  •  ", parts), true, ipv4);
                }
            }
        } catch (Throwable ignored) {
        }
        return new Status("No network address", false, null);
    }

    private static boolean isUsable(NetworkInterface nif) throws SocketException {
        return nif != null && nif.isUp() && !nif.isLoopback() && !nif.isVirtual();
    }

    private static int score(NetworkInterface nif) {
        if (nif == null || nif.getName() == null) return 0;
        String name = nif.getName().toLowerCase();
        if (name.startsWith("wlan")) return 100;
        if (name.startsWith("eth")) return 90;
        if (name.startsWith("usb")) return 80;
        if (name.startsWith("rndis")) return 75;
        if (name.startsWith("rmnet")) return 60;
        return 10;
    }

    private static String shortenIpv6(String value) {
        if (TextUtils.isEmpty(value) || value.length() <= 28) return value;
        return value.substring(0, 24) + "...";
    }
}
