package edu.byu.imaal.dnscapture.util.dnsproxy;

import android.net.VpnService;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import edu.byu.imaal.dnscapture.LogFactory;
import edu.byu.imaal.dnscapture.util.Preferences;

public class SpecialHttpClient {
    private final HttpClient client;
    private static SpecialHttpClient instance = null;

    public static synchronized SpecialHttpClient getInstance(VpnService vpnService) {
        if (instance == null) {
            instance = new SpecialHttpClient(vpnService);
        }
        return instance;
    }

    public static synchronized SpecialHttpClient getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SpecialHttpClient instance is null, and you called it without passing in a VpnService");
        }
        return instance;
    }

    public HttpClient getClient() {
        return client;
    }

    private static final Map<String, InetAddress[]> hostsDotTxt = new HashMap<>();
    static {
        try {
            hostsDotTxt.put("imaal3.byu.edu", new InetAddress[]{
                    InetAddress.getByName("128.187.106.177")
            });
            hostsDotTxt.put("dns.kimballleavitt.com", new InetAddress[]{
                    InetAddress.getByName("34.82.184.125")
            });
            hostsDotTxt.put("my.acer.laptop", new InetAddress[]{
                    InetAddress.getByName("192.168.0.104")
            });
            hostsDotTxt.put("api64.ipify.org", new InetAddress[]{
                    InetAddress.getByName("108.171.202.195"),
                    InetAddress.getByName("108.171.202.203"),
                    InetAddress.getByName("108.171.202.211"),
                    InetAddress.getByName("2607:f2d8:4010:8::2"),
                    InetAddress.getByName("2607:f2d8:4010:b::2"),
            });
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    private SpecialHttpClient(VpnService context) {
        LayeredConnectionSocketFactory secureSocketFactory = new SocketFactory.SecureSocketFactory(context);
        SocketFactory.NormalSocketFactory normalSocketFactory = new SocketFactory.NormalSocketFactory(context);
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", normalSocketFactory)
                .register("https", secureSocketFactory)
                .build();
        final DnsResolver dnsResolver = new SystemDefaultDnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                InetAddress[] addrs = hostsDotTxt.get(host.toLowerCase());
                if (addrs != null) {
                    return addrs;
                }
                return super.resolve(host);
            }
        };
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry, PoolConcurrencyPolicy.STRICT, PoolReusePolicy.LIFO, TimeValue.ofMinutes(5), null, dnsResolver, null);
        connectionManager.setMaxTotal(10);
        connectionManager.setDefaultMaxPerRoute(5);
        String uniqueId = Preferences.getInstance(context).get("unique_client_id", "UNIQUE-ID-NOT-FOUND");
        Header header = new BasicHeader("X-CLIENT-ID", uniqueId);
        List<Header> defaultHeaders = new ArrayList<>();
        defaultHeaders.add(header);
        HttpRequestRetryStrategy retryStrategy = new DefaultHttpRequestRetryStrategy(3, TimeValue.ofMilliseconds(100));
        client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultHeaders(defaultHeaders)
                .setRetryStrategy(retryStrategy)
                .build();
    }

    public void postData(DatagramPacket packet, boolean onBYUsNetwork) {
        HttpPut httpPut = new HttpPut("https://imaal3.byu.edu/dnscapture/");
        HttpEntity body = EntityBuilder.create().setBinary(packet.getData()).build();
        httpPut.setEntity(body);
        httpPut.addHeader("X-DST-IP", packet.getAddress().getHostAddress());
        httpPut.addHeader("X-DST-PORT", packet.getPort());
        httpPut.addHeader("X-FROM-BYU-NETWORK", onBYUsNetwork);
        httpPut.addHeader("X-TIMESTAMP", System.currentTimeMillis());
        httpPut.addHeader("X-REQUEST-ID", UUID.randomUUID().toString());
        CloseableHttpResponse response;
        try {
            response = (CloseableHttpResponse) client.execute(httpPut);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
