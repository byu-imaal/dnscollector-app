package com.frostnerd.dnschanger.database.entities;

public class IPPortPair {
    private String ip;
    private int port;
    private boolean ipv6;

    public IPPortPair(String ip, int port, boolean IPv6) {
        this.ip = ip;
        this.port = port;
        this.ipv6 = IPv6;
    }

    public String getAddress() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public boolean isIpv6() {
        return ipv6;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setIpv6(boolean ipv6) {
        this.ipv6 = ipv6;
    }

    @Override
    public String toString() {
        return ipv6 ? "[" + getAddress() + "]:" + getPort() : getAddress() + ":" + getPort();
    }
}