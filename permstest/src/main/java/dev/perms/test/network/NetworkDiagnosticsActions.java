package dev.perms.test.network;

import android.content.Context;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import dev.perms.test.databinding.TabNetworkBinding;

/**
 * Wires Network tab diagnostic command buttons while the Activity keeps shell execution ownership.
 */
public final class NetworkDiagnosticsActions {
    private NetworkDiagnosticsActions() {}

    public static void bind(Context context, TabNetworkBinding network, ShellCommandRunner runner) {
        if (network == null) return;

        network.btnNetworkIpInfo.setOnClickListener(v -> run(runner,
                "network_ip_info",
                "echo '--- ip addr ---'; ip addr show 2>/dev/null; " +
                        "echo; echo '--- routes ---'; ip route show table all 2>/dev/null | head -n 240; " +
                        "echo; echo '--- net props ---'; getprop | grep -Ei '(^\\[net\\.|dns|dhcp|wifi|wlan|rmnet|radio)' | head -n 180"));

        network.btnNetworkRoutes.setOnClickListener(v -> run(runner,
                "network_routes",
                "ip route show table all 2>/dev/null | head -n 260 || route -n 2>/dev/null || cat /proc/net/route"));

        network.btnNetworkDns.setOnClickListener(v -> run(runner,
                "network_dns",
                "echo '--- DNS properties ---'; getprop | grep -Ei 'dns|dhcp.*dns|net\\.' | head -n 200; " +
                        "echo; echo '--- connectivity DNS hints ---'; dumpsys connectivity 2>/dev/null | grep -i -A 8 -B 3 dns | head -n 180"));

        network.btnNetworkSockets.setOnClickListener(v -> run(runner,
                "network_sockets",
                "ss -tunap 2>/dev/null | head -n 260 || netstat -tunap 2>/dev/null | head -n 260 || " +
                        "cat /proc/net/tcp /proc/net/udp /proc/net/tcp6 /proc/net/udp6 2>/dev/null | head -n 260"));

        network.btnNetworkConnectivity.setOnClickListener(v -> run(runner,
                "network_connectivity",
                "dumpsys connectivity 2>/dev/null | head -n 260"));

        network.btnNetworkPing.setOnClickListener(v -> {
            String host = networkText(network.edtNetworkHost, "google.com");
            run(runner, "network_ping", "ping -c 4 -W 2 " + shellQuote(host));
        });

        network.btnNetworkLookup.setOnClickListener(v -> {
            String host = networkText(network.edtNetworkHost, "google.com");
            String q = shellQuote(host);
            run(runner, "network_lookup",
                    "toybox nslookup " + q + " 2>/dev/null || nslookup " + q + " 2>/dev/null || " +
                            "getent hosts " + q + " 2>/dev/null || ping -c 1 -W 2 " + q);
        });

        network.btnNetworkRouteHost.setOnClickListener(v -> {
            String host = networkText(network.edtNetworkHost, "google.com");
            run(runner, "network_route_host", "ip route get " + shellQuote(host) + " 2>/dev/null || ping -c 1 -W 2 " + shellQuote(host));
        });

        network.btnNetworkHttpHead.setOnClickListener(v -> {
            String url = networkText(network.edtNetworkUrl, "https://example.com");
            String q = shellQuote(url);
            run(runner, "network_http_head",
                    "curl -I -L --max-time 10 " + q + " 2>/dev/null || " +
                            "wget -S --spider -T 10 " + q + " 2>&1 | head -n 160 || " +
                            "toybox wget -S -O - " + q + " 2>&1 | head -n 160");
        });

        network.btnNetworkHttpGet.setOnClickListener(v -> {
            String url = networkText(network.edtNetworkUrl, "https://example.com");
            String q = shellQuote(url);
            run(runner, "network_http_get",
                    "curl -L --max-time 10 " + q + " 2>/dev/null | head -c 8192 || " +
                            "wget -T 10 -O - " + q + " 2>/dev/null | head -c 8192 || " +
                            "toybox wget -O - " + q + " 2>/dev/null | head -c 8192; echo");
        });

        network.btnNetworkTcpCheck.setOnClickListener(v -> {
            String host = networkText(network.edtNetworkPortHost, "google.com");
            String port = networkText(network.edtNetworkPort, "443");
            if (!port.matches("[0-9]{1,5}")) {
                Toast.makeText(context, "Port must be numeric.", Toast.LENGTH_SHORT).show();
                return;
            }
            String h = shellQuote(host);
            String p = shellQuote(port);
            run(runner, "network_tcp_check",
                    "nc -vz -w 3 " + h + " " + p + " 2>&1 || toybox nc -vz -w 3 " + h + " " + p +
                            " 2>&1 || echo 'nc/toybox nc is not available on this device.'");
        });
    }

    private static void run(ShellCommandRunner runner, String tag, String command) {
        if (runner != null) runner.run(tag, command);
    }

    private static String networkText(EditText editText, String fallback) {
        try {
            String s = editText == null || editText.getText() == null ? "" : editText.getText().toString().trim();
            return TextUtils.isEmpty(s) ? fallback : s;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String shellQuote(String value) {
        String s = value == null ? "" : value;
        return "'" + s.replace("'", "'\\''") + "'";
    }

    public interface ShellCommandRunner {
        void run(String tag, String command);
    }
}
