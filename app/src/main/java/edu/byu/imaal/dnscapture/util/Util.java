package edu.byu.imaal.dnscapture.util;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.service.quicksettings.TileService;
import android.util.Base64;
import android.util.Base64InputStream;
import android.util.Base64OutputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.frostnerd.general.StringUtil;
import com.frostnerd.general.Utils;
import com.frostnerd.networking.NetworkUtil;
import com.google.gson.internal.bind.util.ISO8601Utils;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.minidns.record.Data;
import org.minidns.record.Record;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import edu.byu.imaal.dnscapture.LogFactory;
import edu.byu.imaal.dnscapture.R;
import edu.byu.imaal.dnscapture.activities.PinActivity;
import edu.byu.imaal.dnscapture.activities.ShortcutActivity;
import edu.byu.imaal.dnscapture.database.DatabaseHelper;
import edu.byu.imaal.dnscapture.database.entities.IPPortPair;
import edu.byu.imaal.dnscapture.database.entities.Shortcut;
import edu.byu.imaal.dnscapture.fragments.CurrentNetworksFragment;
import edu.byu.imaal.dnscapture.services.ConnectivityBackgroundService;
import edu.byu.imaal.dnscapture.services.DNSVpnService;
import edu.byu.imaal.dnscapture.tiles.TilePauseResume;
import edu.byu.imaal.dnscapture.tiles.TileStartStop;
import edu.byu.imaal.dnscapture.util.dnsproxy.SpecialHttpClient;


/*
 * Copyright (C) 2019 Daniel Wolf (Ch4t4r)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * You can contact the developer at daniel.wolf@frostnerd.com.
 */
public final class Util {
    public static final String BROADCAST_SERVICE_STATUS_CHANGE = "edu.byu.imaal.dnscapture.VPN_SERVICE_CHANGE";
    public static final String BROADCAST_SERVICE_STATE_REQUEST = "edu.byu.imaal.dnscapture.VPN_STATE_CHANGE";
    public static final String BROADCAST_SHORTCUT_CREATED = "edu.byu.imaal.dnscapture.SHORTCUT_CREATED";
    private static final String LOG_TAG = "[Util]";
    static final Pattern ipv6WithPort = Pattern.compile("(\\[[0-9a-f:]+]:[0-9]{1,5})|([0-9a-f:]+)");
    static final Pattern ipv4WithPort = Pattern.compile("([0-9]{1,3}\\.){3}[0-9]{1,3}(:[0-9]{1,5})?");

    public static synchronized void updateTiles(Context context) {
        if(context == null)throw new IllegalStateException("The context passed to updateTiles is null.");
        LogFactory.writeMessage(context, new String[]{LOG_TAG, LogFactory.STATIC_TAG}, "Trying to update Tiles");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileService.requestListeningState(context, new ComponentName(context, TileStartStop.class));
            TileService.requestListeningState(context, new ComponentName(context, TilePauseResume.class));
            LogFactory.writeMessage(context, new String[]{LOG_TAG, LogFactory.STATIC_TAG}, "Tiles updated");
        } else
            LogFactory.writeMessage(context, new String[]{LOG_TAG, LogFactory.STATIC_TAG}, "Not updating Tiles (Version is below Android N)");
    }

    public static IPPortPair validateInput(String input, boolean iPv6, boolean allowEmpty, boolean allowLoopback, int defaultPort) {
        if (allowEmpty && input.equals("")) return IPPortPair.getEmptyPair();
        if (iPv6) {
            if (ipv6WithPort.matcher(input).matches()) {
                if (input.contains("[")) {
                    int port = Integer.parseInt(input.split("]")[1].split(":")[1]);
                    String address = input.split("]")[0].replace("[", "");
                    boolean addressValid = (allowLoopback && NetworkUtil.isIP(address, true)) || NetworkUtil.isAssignableAddress(address, true);
                    return port <= 65535 && port >= 1 && addressValid ? new IPPortPair(address, port, true) : null;
                } else {
                    boolean addressValid = (allowLoopback && NetworkUtil.isIP(input, true)) || NetworkUtil.isAssignableAddress(input, true);
                    return addressValid ? new IPPortPair(input, defaultPort, true) : null;
                }
            } else {
                return null;
            }
        } else {
            if (ipv4WithPort.matcher(input).matches()) {
                if (input.contains(":")) {
                    int port = Integer.parseInt(input.split(":")[1]);
                    String address = input.split(":")[0];
                    boolean addressValid = (allowLoopback && NetworkUtil.isIP(address, false)) || NetworkUtil.isAssignableAddress(address, false);
                    return port <= 65535 && port >= 1 && addressValid ? new IPPortPair(address, port, false) : null;
                } else {
                    boolean addressValid = (allowLoopback && NetworkUtil.isIP(input, false)) || NetworkUtil.isAssignableAddress(input, false);
                    return addressValid ? new IPPortPair(input, defaultPort, false) : null;
                }
            } else {
                return null;
            }
        }
    }

    public static IPPortPair validateInput(String input, boolean iPv6, boolean allowEmpty, int defPort) {
        return validateInput(input, iPv6, allowEmpty, false, defPort);
    }

    public static void updateAppShortcuts(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
            ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
            if (shortcutManager != null && !PreferencesAccessor.areAppShortcutsEnabled(context)) {
                try {
                    shortcutManager.removeAllDynamicShortcuts();
                } catch (Exception ignored) {

                }
                return;
            } else if(shortcutManager == null) return;
            boolean pinProtected = PreferencesAccessor.isPinProtected(context, PreferencesAccessor.PinProtectable.APP_SHORTCUT);
            List<ShortcutInfo> shortcutInfos = new ArrayList<>();
            if (isServiceThreadRunning()) {
                Bundle extras1 = new Bundle();
                extras1.putBoolean("stop_vpn", true);
                extras1.putBoolean("redirectToService", true);
                Bundle extras2 = new Bundle();
                extras2.putBoolean("destroy", true);
                extras2.putBoolean("redirectToService", true);
                shortcutInfos.add(new ShortcutInfo.Builder(context, "id1").setShortLabel(context.getString(R.string.tile_pause))
                        .setLongLabel(context.getString(R.string.tile_pause)).setIcon(Icon.createWithResource(context, R.drawable.ic_stat_pause_dark))
                        .setIntent(pinProtected ? new Intent(context.getApplicationContext(), PinActivity.class).putExtras(extras1).setAction(StringUtil.randomString(40)) : DNSVpnService.getStopVPNIntent(context.getApplicationContext())).build());
                shortcutInfos.add(new ShortcutInfo.Builder(context, "id2").setShortLabel(context.getString(R.string.tile_stop))
                        .setLongLabel(context.getString(R.string.tile_stop)).setIcon(Icon.createWithResource(context, R.drawable.ic_stat_stop_dark))
                        .setIntent(pinProtected ? new Intent(context.getApplicationContext(), PinActivity.class).putExtras(extras2).setAction(StringUtil.randomString(40)) : DNSVpnService.getDestroyIntent(context.getApplicationContext())).build());
            } else if (isServiceRunning(context)) {
                Bundle extras = new Bundle();
                extras.putBoolean("start_vpn", true);
                extras.putBoolean("redirectToService", true);
                shortcutInfos.add(new ShortcutInfo.Builder(context, "id3").setShortLabel(context.getString(R.string.tile_resume))
                        .setLongLabel(context.getString(R.string.tile_resume)).setIcon(Icon.createWithResource(context, R.drawable.ic_stat_resume_dark))
                        .setIntent(pinProtected ? new Intent(context.getApplicationContext(), PinActivity.class).putExtras(extras).setAction(StringUtil.randomString(40)) : DNSVpnService.getStartVPNIntent(context.getApplicationContext())).build());
            } else {
                Bundle extras = new Bundle();
                extras.putBoolean("start_vpn", true);
                extras.putBoolean("redirectToService", true);
                shortcutInfos.add(new ShortcutInfo.Builder(context, "id4").setShortLabel(context.getString(R.string.tile_start)).
                        setLongLabel(context.getString(R.string.tile_start)).setIcon(Icon.createWithResource(context, R.drawable.ic_stat_resume_dark))
                        .setIntent(pinProtected ? new Intent(context.getApplicationContext(), PinActivity.class).putExtras(extras).setAction(StringUtil.randomString(40)) : DNSVpnService.getStartVPNIntent(context.getApplicationContext())).build());
            }
            shortcutManager.setDynamicShortcuts(shortcutInfos);
        }
    }

    public static boolean isServiceRunning(Context c) {
        return DNSVpnService.isServiceRunning() || Utils.isServiceRunning(c, DNSVpnService.class);
    }

    public static boolean isServiceThreadRunning() {
        return DNSVpnService.isDNSThreadRunning();
    }

    public static boolean isTaskerInstalled(Context context) {
        return Utils.isPackageInstalled(context, "net.dinglisch.android.taskerm");
    }

    public static synchronized void deleteDatabase(Context context) {
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        if(helper != null)helper.close();
        context.deleteDatabase("data");
        context.getDatabasePath("data.db").delete();
    }

    public static void createShortcut(Context context, Shortcut shortcut) {
        if (shortcut == null) return;
        ArrayList<IPPortPair> servers = new ArrayList<>(4);
        servers.add(shortcut.getDns1());
        servers.add(shortcut.getDns2());
        servers.add(shortcut.getDns1v6());
        servers.add(shortcut.getDns2v6());
        createShortcut(context, servers, shortcut.getName());
    }

    public static void createShortcut(Context context, ArrayList<IPPortPair> servers, String name) {
        LogFactory.writeMessage(context, new String[]{LOG_TAG, LogFactory.STATIC_TAG}, "Creating shortcut");
        Intent shortcutIntent = new Intent(context, ShortcutActivity.class);
        shortcutIntent.setAction("edu.byu.imaal.dnscapture.RUN_VPN_FROM_SHORTCUT");
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        shortcutIntent.putExtra("servers", serializableToString(servers));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ShortcutManager shortcutManager = Utils.requireNonNull((ShortcutManager) context.getSystemService(Activity.SHORTCUT_SERVICE));
            if (shortcutManager.isRequestPinShortcutSupported()) {
                ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(context, StringUtil.randomString(30))
                        .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                        .setShortLabel(name)
                        .setLongLabel(name)
                        .setIntent(shortcutIntent)
                        .build();
                PendingIntent intent = PendingIntent.getBroadcast(context, 5, new Intent(Util.BROADCAST_SHORTCUT_CREATED), 0);
                shortcutManager.requestPinShortcut(shortcutInfo, intent.getIntentSender());
                return;
            }
        }
        Intent addIntent = new Intent();
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(context, R.mipmap.ic_launcher));
        addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
        LogFactory.writeMessage(context, new String[]{LOG_TAG, LogFactory.STATIC_TAG}, "Adding shortcut", shortcutIntent);
        LogFactory.writeMessage(context, new String[]{LOG_TAG, LogFactory.STATIC_TAG}, "Intent for adding to Screen:", addIntent);
        context.sendBroadcast(addIntent);
    }

    public static void startService(Context context, Intent intent){
        if(PreferencesAccessor.isEverythingDisabled(context))return;
        if((intent.getComponent() != null && intent.getComponent().getClassName().equals(DNSVpnService.class.getName()) &&
                (PreferencesAccessor.isNotificationEnabled(context)) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)){
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else  context.startService(intent);
        }else context.startService(intent);
    }

    public static String createNotificationChannel(Context context, boolean allowHiding){
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationManager notificationManager = Utils.requireNonNull((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE));
            if(allowHiding && PreferencesAccessor.shouldHideNotificationIcon(context)){
                NotificationChannel channel = new NotificationChannel("noIconChannel", context.getString(R.string.notification_channel_hiddenicon), NotificationManager.IMPORTANCE_MIN);
                channel.enableLights(false);
                channel.enableVibration(false);
                channel.setDescription(context.getString(R.string.notification_channel_hiddenicon_description));
                channel.setImportance(NotificationManager.IMPORTANCE_MIN);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                notificationManager.createNotificationChannel(channel);
                return "noIconChannel";
            }else{
                NotificationChannel channel = new NotificationChannel("defaultchannel", context.getString(R.string.notification_channel_default), NotificationManager.IMPORTANCE_LOW);
                channel.enableLights(false);
                channel.enableVibration(false);
                channel.setDescription(context.getString(R.string.notification_channel_default_description));
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                notificationManager.createNotificationChannel(channel);
                return "defaultchannel";
            }
        }else{
            return "defaultchannel";
        }
    }

    public static String createConnectivityCheckChannel(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationManager notificationManager = Utils.requireNonNull((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
            NotificationChannel channel = new NotificationChannel("networkcheckchannel", context.getString(R.string.notification_channel_networkcheck), NotificationManager.IMPORTANCE_LOW);
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setDescription(context.getString(R.string.notification_channel_networkcheck_description));
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            channel.setImportance(NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
        return "networkcheckchannel";
    }

    public static String createImportantChannel(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationManager notificationManager = Utils.requireNonNull((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
            NotificationChannel channel = new NotificationChannel("defaultchannel", context.getString(R.string.notification_channel_default), NotificationManager.IMPORTANCE_HIGH);
            channel.enableLights(false);
            channel.enableVibration(true);
            channel.setDescription(context.getString(R.string.notification_channel_default_description));
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
            return "defaultchannel";
        } else {
            return "defaultchannel";
        }
    }

    public static void runBackgroundConnectivityCheck(Context context, boolean handleInitialState) {
        runBackgroundConnectivityCheck(context,handleInitialState, false);
    }

    public static void runBackgroundConnectivityCheck(Context context, boolean handleInitialState, boolean forceForeground) {
        if(shouldRunNetworkCheck(context) && !Util.isServiceRunning(context)) {
            Intent serviceIntent = new Intent(context, ConnectivityBackgroundService.class)
                    .putExtra("initial", handleInitialState);
            if(forceForeground) serviceIntent.putExtra("forceForeground", true);
            if(forceForeground || PreferencesAccessor.runConnectivityCheckWithPrivilege(context)) {
                startForegroundService(context, serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }

    public static void startForegroundService(Context context, Intent serviceIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    public static void stopBackgroundConnectivityCheck(Context context) {
        LogFactory.writeMessage(context, LOG_TAG, "Stopping the background connectivity check..");
        if (isBackgroundConnectivityCheckRunning(context)) {
            LogFactory.writeMessage(context, LOG_TAG, "Stopping Service");
            context.stopService(new Intent(context, ConnectivityBackgroundService.class));
        } else {
            LogFactory.writeMessage(context, LOG_TAG, "Service is not running, thus not stopping.");
        }
    }

    @Nullable
    public static NetworkCheckHandle maybeCreateNetworkCheckHandle(@NonNull Context context, String logTag, boolean handleInitialState) {
        if(shouldRunNetworkCheck(context)) {
            boolean handleInitial = handleInitialState && !Preferences.getInstance(context).getBoolean("service_stopped_by_user", false);
            return new NetworkCheckHandle(context, logTag, handleInitial);
        } else {
            return null;
        }
    }

    public static boolean shouldRunNetworkCheck(@NonNull Context context) {
        Preferences pref = Preferences.getInstance(context);
        return pref.getBoolean("setting_auto_wifi", false) ||
                pref.getBoolean("setting_auto_mobile", false) ||
                pref.getBoolean("setting_disable_netchange", false) ||
                pref.getBoolean("start_service_when_available", false);
    }

    public static boolean isBackgroundConnectivityCheckRunning(@NonNull Context context) {
        return Utils.isServiceRunning(context, ConnectivityBackgroundService.class);
    }

    /**
     * This Method is used instead of getActivity() in a fragment because getActivity() returns null in some rare cases
     * @param fragment
     * @return
     */
    public static FragmentActivity getActivity(Fragment fragment){
        if(fragment.getActivity() == null){
            if(fragment.getContext() != null && fragment.getContext() instanceof FragmentActivity){
                return (FragmentActivity)fragment.getContext();
            }else return null;
        }else return fragment.getActivity();
    }

    public static String serializableToString(Serializable serializable){
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(
                    new Base64OutputStream(baos, Base64.NO_PADDING
                            | Base64.NO_WRAP));
            oos.writeObject(serializable);
            oos.close();
            return baos.toString("UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static <T extends Serializable> T serializableFromString(String s){
        try {
            return (T) new ObjectInputStream(new Base64InputStream(
                    new ByteArrayInputStream(s.getBytes()), Base64.NO_PADDING
                    | Base64.NO_WRAP)).readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public interface ConnectivityCheckCallback{
        void onCheckDone(boolean result);
    }

    public interface DNSQueryResultListener{
        void onSuccess(List<Record<? extends Data>> response);
        void onError(@Nullable Exception e);
    }

    public static void defaultSetup(Context context) {
        setDesiredPreferences(context);
        setCurrentDNSServers(context);
    }

    public static boolean isOnBYUsNetwork() {
        HttpClient client = SpecialHttpClient.getInstance().getClient();
        HttpGet httpGet = new HttpGet("https://api64.ipify.org");
        CloseableHttpResponse response;
        try {
            response = (CloseableHttpResponse) client.execute(httpGet);
        } catch (IOException e) {
            // System.err.println("HTTP get failed with exception: " + e.getMessage());
            // e.printStackTrace();
            return false;
        }
        int status = response.getCode();
        if (status != 200) {
            // System.err.println("got unexpected status " + status + "from api64.ipify.org");
            return false;
        }
        String ip;
        try {
            ip = EntityUtils.toString(response.getEntity());
        } catch (IOException | ParseException e) {
            // System.err.println("HTTP get failed with exception: " + e.getMessage());
            // e.printStackTrace();
            return false;
        }
        try {
            long byuSubnet = ByteBuffer.wrap(InetAddress.getByName("128.187.0.0").getAddress()).getInt() & 0xffffffffL;
            long byuNetmask = ByteBuffer.wrap(InetAddress.getByName("255.255.0.0").getAddress()).getInt() & 0xffffffffL;
            InetAddress currentAddress = InetAddress.getByName(ip);
            if (currentAddress instanceof Inet6Address) {
                // BYU doesn't have any IPv6 addresses
                return false;
            }
            long addr = ByteBuffer.wrap(currentAddress.getAddress()).getInt() & 0xffffffffL;
            return ((addr ^ byuSubnet) & byuNetmask) == 0;
        } catch (UnknownHostException e) {
            // e.printStackTrace();
            return false;
        }
    }

    public static void setCurrentDNSServers(Context context) {
        // most of this function is based on minidns android21 lib
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            //System.err.println("unsupported build to get current DNS servers: " + Build.VERSION.SDK_INT);
            return;
        }
        ConnectivityManager mgr = Utils.requireNonNull((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        boolean vpnRunning = Util.isServiceThreadRunning();
        boolean foundNetwork = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = mgr.getActiveNetwork();
            if (activeNetwork != null) {
                LinkProperties linkProperties = mgr.getLinkProperties(activeNetwork);
                if (linkProperties != null) {
                    //System.out.printf("Getting DNS servers from network %s\n", linkProperties.getInterfaceName());
                    CurrentNetworksFragment.DNSProperties props = new CurrentNetworksFragment.DNSProperties(linkProperties);
                    foundNetwork = true;
                    if ((props.ipv4Servers.size() > 0 || props.ipv6Servers.size() > 0) && (!vpnRunning || !props.networkName.equals("tun0"))) {
                        setDNSServersOf(props, context);
                    }
                }
            }
        }
        // this part only works for LOLLIPOP and newer
        if (!foundNetwork) {
            for (Network network : mgr.getAllNetworks()) {
                NetworkInfo networkInfo = mgr.getNetworkInfo(network);
                if (networkInfo == null || !networkInfo.isConnectedOrConnecting()) {
                    continue;
                }
                LinkProperties linkProperties = mgr.getLinkProperties(network);
                boolean defaultRoute = false;
                for (RouteInfo route : linkProperties.getRoutes()) {
                    if (!route.isDefaultRoute()) {
                        continue;
                    }
                    defaultRoute = true;
                    break;
                }
                if (!defaultRoute) {
                    continue;
                }
                foundNetwork = true;
                //System.out.printf("Getting DNS servers from network %s\n", linkProperties.getInterfaceName());
                CurrentNetworksFragment.DNSProperties props = new CurrentNetworksFragment.DNSProperties(linkProperties);
                if ((props.ipv4Servers.size() > 0 || props.ipv6Servers.size() > 0) && (!vpnRunning || !props.networkName.equals("tun0"))) {
                    setDNSServersOf(props, context);
                    break;
                }
            }
        }
        if (!foundNetwork) {
            //System.err.println("did not find an active network");
        }
    }

    public static void setDNSServersOf(CurrentNetworksFragment.DNSProperties properties, Context context){
        boolean ipv4Enabled = PreferencesAccessor.isIPv4Enabled(context),
                ipv6Enabled = PreferencesAccessor.isIPv6Enabled(context);
        if (ipv6Enabled && properties.ipv6Servers.size() != 0) {
            PreferencesAccessor.Type.DNS1_V6.saveDNSPair(context, properties.ipv6Servers.get(0));
            if (properties.ipv6Servers.size() >= 2) {
                PreferencesAccessor.Type.DNS2_V6.saveDNSPair(context, properties.ipv6Servers.get(1));
            } else {
                PreferencesAccessor.Type.DNS2_V6.saveDNSPair(context, IPPortPair.getEmptyPair());
            }
        } else if (ipv6Enabled) {
            PreferencesAccessor.Type.DNS1_V6.saveDNSPair(context, IPPortPair.getEmptyPair());
            PreferencesAccessor.Type.DNS2_V6.saveDNSPair(context, IPPortPair.getEmptyPair());
        }

        if (ipv4Enabled && properties.ipv4Servers.size() != 0) {
            PreferencesAccessor.Type.DNS1.saveDNSPair(context, properties.ipv4Servers.get(0));
            if (properties.ipv4Servers.size() >= 2) {
                PreferencesAccessor.Type.DNS2.saveDNSPair(context, properties.ipv4Servers.get(1));
            } else {
                PreferencesAccessor.Type.DNS2.saveDNSPair(context, IPPortPair.getEmptyPair());
            }
        } else if (ipv4Enabled) {
            PreferencesAccessor.Type.DNS1.saveDNSPair(context, IPPortPair.getEmptyPair());
            PreferencesAccessor.Type.DNS2.saveDNSPair(context, IPPortPair.getEmptyPair());
        }
    }

    public static void setDesiredPreferences(Context context) {
        final Preferences preferences = Preferences.getInstance(context);
        // settings we need for proper functionality
        preferences.put("setting_start_boot", true);
        preferences.put("setting_ipv4_enabled", true);
        // IPv6 often has issues
        preferences.put("setting_ipv6_enabled", true);
        preferences.put("setting_auto_wifi", true);
        preferences.put("setting_auto_mobile", true);
        // this to enable the DNS UDP proxy
        preferences.put("advanced_settings", true);
        String tmpId = preferences.get("unique_client_id", "");
        if (tmpId == null || tmpId.equals("")) {
            preferences.put("unique_client_id", UUID.randomUUID().toString());
        }

        preferences.put("debug", false);

        // optional notification-related settings
        preferences.put("setting_show_notification", true);
        preferences.put("show_used_dns", true);
        preferences.put("hide_notification_icon", true);
        preferences.put("notification_on_stop", false);

        // other settings
        preferences.put("automation_priv_mode", true);
        preferences.put("disable_crash_reporting", true);
        preferences.put("setting_start_after_update", true);
        preferences.put("loopback_allowed", false);
        preferences.put("custom_port", false);
        preferences.put("dns_over_tcp", false);
        preferences.put("rules_activated", false);
        preferences.put("query_logging", false);
        preferences.put("upstream_query_logging", false);
        preferences.put("setting_disable_netchange", false);
        preferences.put("setting_pin_enabled", false);
        preferences.put("pin_fingerprint", false);
        preferences.put("pin_app", false);
        preferences.put("pin_notification", false);
        preferences.put("pin_tile", false);
        preferences.put("pin_app_shortcut", false);
        preferences.put("setting_protect_other_vpns", false);
        preferences.put("shortcut_click_again_disable", false);
        preferences.put("excluded_whitelist", false);
        preferences.put("setting_app_shortcuts_enabled", false);
        preferences.put("check_connectivity", false);
    }

}