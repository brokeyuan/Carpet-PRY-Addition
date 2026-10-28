package me.primaryuan.carpet;

/**
 * 「地址:端口」纯解析值类型：不触发 DNS 与网络连接，不依赖 Minecraft 类。
 *
 * <p>接受 域名:端口、IPv4:端口、[IPv6]:端口（含 localhost）；端口必须显式给出
 * 且为 1–65535 的十进制整数。拒绝空主机、缺/空/越界端口、非法括号、无方括号
 * 的 IPv6、URL 协议头、路径、查询串与空白/控制字符。</p>
 *
 * @param host 主机（IPv6 为不带方括号的字面量）
 * @param port 端口 1–65535
 */
public record TptoAddress(String host, int port) {

    private static final int MAX_PORT = 65535;

    /** 解析「地址:端口」；非法输入抛 {@link IllegalArgumentException} */
    public static TptoAddress parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("address is empty");
        }
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c <= ' ' || c == 0x7F) {
                throw new IllegalArgumentException("address contains whitespace or control character");
            }
            if (c == '/' || c == '?' || c == '#') {
                throw new IllegalArgumentException("address must be host:port, not a URL/path");
            }
        }
        if (input.contains("://")) {
            throw new IllegalArgumentException("address must be host:port, not a URL");
        }

        String host;
        String portPart;
        if (input.startsWith("[")) {
            int close = input.indexOf(']');
            if (close < 0) {
                throw new IllegalArgumentException("unclosed bracket in IPv6 address");
            }
            if (input.indexOf('[', 1) >= 0 || input.indexOf(']', close + 1) >= 0) {
                throw new IllegalArgumentException("misplaced bracket in address");
            }
            host = input.substring(1, close);
            if (host.isBlank()) {
                throw new IllegalArgumentException("empty host");
            }
            if (close + 1 >= input.length() || input.charAt(close + 1) != ':') {
                throw new IllegalArgumentException("port is required after [host]");
            }
            portPart = input.substring(close + 2);
        } else {
            int colon = input.indexOf(':');
            if (colon < 0) {
                throw new IllegalArgumentException("port is required (host:port)");
            }
            if (input.indexOf(':', colon + 1) >= 0) {
                throw new IllegalArgumentException("bare IPv6 must be written as [ipv6]:port");
            }
            host = input.substring(0, colon);
            portPart = input.substring(colon + 1);
        }

        int port = parsePort(portPart);
        validateHost(host);
        return new TptoAddress(host, port);
    }

    /** 校验文件中单独存放的 host 字段（不含端口；IPv6 为裸字面量） */
    public static void validateHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("empty host");
        }
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c <= ' ' || c == 0x7F || c == '/' || c == '?' || c == '#' || c == '[' || c == ']') {
                throw new IllegalArgumentException("invalid character in host: " + host);
            }
        }
    }

    private static int parsePort(String portPart) {
        if (portPart.isEmpty()) {
            throw new IllegalArgumentException("empty port");
        }
        for (int i = 0; i < portPart.length(); i++) {
            char c = portPart.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("port must be a decimal integer: " + portPart);
            }
        }
        int port = Integer.parseInt(portPart);
        if (port < 1 || port > MAX_PORT) {
            throw new IllegalArgumentException("port out of range 1-" + MAX_PORT + ": " + portPart);
        }
        return port;
    }

    /** 展示格式：IPv6 输出为 [host]:port */
    public String format() {
        return format(host, port);
    }

    /** 展示格式（host 与 port 分开的场景，如悬停提示） */
    public static String format(String host, int port) {
        return host.indexOf(':') >= 0 ? "[" + host + "]:" + port : host + ":" + port;
    }
}
