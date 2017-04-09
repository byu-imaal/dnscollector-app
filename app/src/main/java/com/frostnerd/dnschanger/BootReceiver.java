package com.frostnerd.dnschanger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;

import com.frostnerd.dnschanger.activities.BackgroundVpnConfigureActivity;
import com.frostnerd.dnschanger.services.ConnectivityBackgroundService;
import com.frostnerd.dnschanger.services.DNSVpnService;
import com.frostnerd.utils.preferences.Preferences;

/**
 * Copyright Daniel Wolf 2017
 * All rights reserved.
 *
 * Terms on usage of my code can be found here: https://git.frostnerd.com/PublicAndroidApps/DnsChanger/blob/master/README.md
 *
 * <p>
 * development@frostnerd.com
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        context.startService(new Intent(context, ConnectivityBackgroundService.class));
        if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) || intent.getAction().equals(Intent.ACTION_LOCKED_BOOT_COMPLETED)){
            if(Preferences.getBoolean(context,"setting_start_boot",false)){
                Intent i = VpnService.prepare(context);
                if(i == null){
                    context.startService(new Intent(context, DNSVpnService.class).putExtra("start_vpn",true).putExtra("startedWithTasker", false));
                }
                else BackgroundVpnConfigureActivity.startBackgroundConfigure(context,true);
            }
        }
    }
}
