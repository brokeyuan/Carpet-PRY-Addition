package me.primaryuan.carpet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link TptoAddress} 边界：合法形态、端口约束、URL/路径/IPv6/括号拒绝与展示格式 */
class TptoAddressTest {

    @Test
    void parsesValidForms() {
        assertEquals(new TptoAddress("survival.example.com", 25565),
                TptoAddress.parse("survival.example.com:25565"));
        assertEquals(new TptoAddress("192.168.1.10", 25566),
                TptoAddress.parse("192.168.1.10:25566"));
        assertEquals(new TptoAddress("localhost", 25565),
                TptoAddress.parse("localhost:25565"));
        assertEquals(new TptoAddress("::1", 25565),
                TptoAddress.parse("[::1]:25565"));
        assertEquals(new TptoAddress("2001:db8::1", 25566),
                TptoAddress.parse("[2001:db8::1]:25566"));
        assertEquals(1, TptoAddress.parse("a:1").port());
        assertEquals(65535, TptoAddress.parse("a:65535").port());
    }

    @Test
    void rejectsInvalidForms() {
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse(null));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse(""));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("   "));
        // 缺端口 / 空端口 / 端口越界 / 非十进制
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("survival.example.com"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("host:"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("host:0"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("host:65536"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("host:-1"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("host:+25565"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("host:25565 "));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("host:abc"));
        // 空主机 / 无方括号 IPv6 / 非法括号
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse(":25565"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("::1"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("[::1]"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("[::1"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("[a:b]x:25565"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("[]:25565"));
        // URL 协议头 / 路径 / 查询串
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("http://host:25565"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("host:25565/path"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("host:25565?q=1"));
        // 空白与控制字符
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("ho st:25565"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("host :25565"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.parse("host:\t25565"));
    }

    @Test
    void validateHostAcceptsBareIpv6AndRejectsGarbage() {
        TptoAddress.validateHost("survival.example.com");
        TptoAddress.validateHost("::1");
        TptoAddress.validateHost("localhost");
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.validateHost(""));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.validateHost(" "));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.validateHost("a b"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.validateHost("a/b"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.validateHost("a?b"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.validateHost("a#b"));
        assertThrows(IllegalArgumentException.class, () -> TptoAddress.validateHost("[::1]"));
    }

    @Test
    void formatsIpv6WithBrackets() {
        assertEquals("survival.example.com:25565", TptoAddress.format("survival.example.com", 25565));
        assertEquals("[::1]:25565", TptoAddress.format("::1", 25565));
        assertEquals("[::1]:25565", TptoAddress.parse("[::1]:25565").format());
        assertEquals("localhost:25565", TptoAddress.parse("localhost:25565").format());
    }
}
