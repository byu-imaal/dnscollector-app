package com.frostnerd.dnschanger.activities;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.frostnerd.dnschanger.API;
import com.frostnerd.dnschanger.LogFactory;
import com.frostnerd.dnschanger.services.ConnectivityBackgroundService;
import com.frostnerd.dnschanger.services.DNSVpnService;
import com.frostnerd.dnschanger.R;
import com.frostnerd.dnschanger.tasker.ConfigureActivity;
import com.frostnerd.utils.design.MaterialEditText;
import com.frostnerd.utils.general.Utils;
import com.frostnerd.utils.preferences.Preferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * Copyright Daniel Wolf 2017
 * All rights reserved.
 *
 * Terms on usage of my code can be found here: https://git.frostnerd.com/PublicAndroidApps/DnsChanger/blob/master/README.md
 *
 * <p>
 * development@frostnerd.com
 */
public class MainActivity extends AppCompatActivity {
    private Button startStopButton;
    private boolean vpnRunning, wasStartedWithTasker = false;
    private MaterialEditText met_dns1, met_dns2;
    private EditText dns1, dns2;
    private static final HashMap<String, List<String>> defaultDNS = new HashMap<>();
    private static final HashMap<String, List<String>> defaultDNS_V6 = new HashMap<>();
    private static final List<String> defaultDNSKeys, DefaultDNSKeys_V6;
    private boolean doStopVPN = true;
    private static final String LOG_TAG = "[MainActivity]";

    private TextView connectionText;
    private ImageView connectionImage;
    private LinearLayout defaultDNSView;
    private Button rate, info;
    private ImageButton importButton;
    private View running_indicator;

    private AlertDialog defaultDnsDialog;
    private LinearLayout wrapper;
    private boolean settingV6 = false;

    @Override
    protected void onDestroy() {
        LogFactory.writeMessage(this, LOG_TAG, "Destroying");
        if(dialog1 != null)dialog1.cancel();
        if(dialog2 != null)dialog2.cancel();
        if(defaultDnsDialog != null)defaultDnsDialog.cancel();
        LogFactory.writeMessage(this, LOG_TAG, "Destroyed");
        super.onDestroy();
    }

    private AlertDialog dialog1, dialog2;

    private BroadcastReceiver serviceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LogFactory.writeMessage(MainActivity.this, LOG_TAG, "Received ServiceState Answer", intent);
            vpnRunning = intent.getBooleanExtra("vpn_running",false);
            wasStartedWithTasker = intent.getBooleanExtra("started_with_tasker", false);
            setIndicatorState(intent.getBooleanExtra("vpn_running",false));
        }
    };

    static {
        defaultDNS.put("Google DNS", Arrays.asList("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844"));
        defaultDNS.put("OpenDNS", Arrays.asList("208.67.222.222", "208.67.220.220", "2620:0:ccc::2", "2620:0:ccd::2"));
        defaultDNS.put("Level3", Arrays.asList("209.244.0.3", "209.244.0.4"));
        defaultDNS.put("FreeDNS", Arrays.asList("37.235.1.174", "37.235.1.177"));
        defaultDNS.put("Yandex DNS", Arrays.asList("77.88.8.8", "77.88.8.1", "2a02:6b8::feed:0ff", "2a02:6b8:0:1::feed:0ff"));
        defaultDNS.put("Verisign", Arrays.asList("64.6.64.6", "64.6.65.6", "2620:74:1b::1:1", "2620:74:1c::2:2"));
        defaultDNS.put("Alternate DNS", Arrays.asList("198.101.242.72", "23.253.163.53"));
        defaultDNS.put("Norton Connectsafe - Security", Arrays.asList("199.85.126.10", "199.85.127.10"));
        defaultDNS.put("Norton Connectsafe - Security + Pornography", Arrays.asList("199.85.126.20", "199.85.127.20"));
        defaultDNS.put("Norton Connectsafe - Security + Pornography + Other", Arrays.asList("199.85.126.30", "199.85.127.30"));

        defaultDNS_V6.put("Google DNS", Arrays.asList("2001:4860:4860::8888", "2001:4860:4860::8844"));
        defaultDNS_V6.put("OpenDNS", Arrays.asList("2620:0:ccc::2", "2620:0:ccd::2"));
        defaultDNS_V6.put("Yandex DNS", Arrays.asList("2a02:6b8::feed:0ff", "2a02:6b8:0:1::feed:0ff"));
        defaultDNS_V6.put("Verisign", Arrays.asList("2620:74:1b::1:1", "2620:74:1c::2:2"));
        defaultDNSKeys = new ArrayList<>(defaultDNS.keySet());
        DefaultDNSKeys_V6 = new ArrayList<>(defaultDNS_V6.keySet());
        Collections.sort(defaultDNSKeys);
        Collections.sort(defaultDNSKeys);
    }

    private void setIndicatorState(boolean vpnRunning) {
        LogFactory.writeMessage(this, LOG_TAG, "Changing IndicatorState to " + vpnRunning);
        if (vpnRunning) {
            int color = Color.parseColor("#42A5F5");
            connectionText.setText(R.string.running);
            connectionImage.setImageResource(R.drawable.ic_thumb_up);
            startStopButton.setBackgroundColor(color);
            met_dns1.setCardColor(color);
            met_dns1.setCardStrokeColor(color);
            met_dns2.setCardColor(color);
            met_dns2.setCardStrokeColor(color);
            defaultDNSView.setBackgroundColor(color);
            rate.setBackgroundColor(color);
            info.setBackgroundColor(color);
            importButton.setBackgroundColor(color);
            startStopButton.setText(R.string.stop);
            running_indicator.setBackgroundColor(Color.parseColor("#4CAF50"));
        } else {
            int color = Color.parseColor("#42A5F5");
            connectionText.setText(R.string.not_running);
            connectionImage.setImageResource(R.drawable.ic_thumb_down);
            startStopButton.setBackgroundColor(color);
            met_dns1.setCardColor(color);
            met_dns1.setCardStrokeColor(color);
            met_dns2.setCardColor(color);
            met_dns2.setCardStrokeColor(color);
            defaultDNSView.setBackgroundColor(color);
            rate.setBackgroundColor(color);
            info.setBackgroundColor(color);
            importButton.setBackgroundColor(color);
            startStopButton.setText(R.string.start);
            running_indicator.setBackgroundColor(Color.parseColor("#2196F3"));
        }
        LogFactory.writeMessage(this, LOG_TAG, "IndictorState set");
    }

    public void rateApp(View v) {
        final String appPackageName = getPackageName();
        LogFactory.writeMessage(this, LOG_TAG, "Opening site to rate app");
        try {
            LogFactory.writeMessage(this, LOG_TAG, "Trying to open market");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
            LogFactory.writeMessage(this, LOG_TAG, "Market was opened");
        } catch (android.content.ActivityNotFoundException e) {
            LogFactory.writeMessage(this, LOG_TAG, "Market not present. Opening with general ACTION_VIEW");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        }
        Preferences.put(MainActivity.this, "rated",true);
    }

    public void openDNSInfoDialog(View v) {
        LogFactory.writeMessage(this, LOG_TAG, "Opening Dialog with info about DNS");
        dialog1 = new AlertDialog.Builder(this).setTitle(R.string.info_dns_button).setMessage(R.string.dns_info_text).setCancelable(true).setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        }).show();
        LogFactory.writeMessage(this, LOG_TAG, "Dialog is now being shown");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogFactory.writeMessage(this, LOG_TAG, "Created Activity", getIntent());
        DNSVpnService.updateTiles(this);
        LogFactory.writeMessage(this, LOG_TAG, "Launching ConnectivityBackgroundService");
        startService(new Intent(this, ConnectivityBackgroundService.class));
        LogFactory.writeMessage(this, LOG_TAG, "Setting ContentView");
        setContentView(R.layout.activity_main);
        met_dns1 = (MaterialEditText) findViewById(R.id.met_dns1);
        met_dns2 = (MaterialEditText) findViewById(R.id.met_dns2);
        dns1 = (EditText) findViewById(R.id.dns1);
        dns2 = (EditText) findViewById(R.id.dns2);
        connectionImage = (ImageView)findViewById(R.id.connection_status_image);
        connectionText = (TextView)findViewById(R.id.connection_status_text);
        defaultDNSView = (LinearLayout)findViewById(R.id.default_dns_view);
        rate = (Button)findViewById(R.id.rate);
        info = (Button)findViewById(R.id.dnsInfo);
        wrapper = (LinearLayout)findViewById(R.id.activity_main);
        importButton = (ImageButton)findViewById(R.id.default_dns_view_image);
        running_indicator = (View)findViewById(R.id.running_indicator);
        dns1.setText(Preferences.getString(MainActivity.this, "dns1", "8.8.8.8"));
        dns2.setText(Preferences.getString(MainActivity.this, "dns2", "8.8.4.4"));
        startStopButton = (Button) findViewById(R.id.startStopButton);
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent i = VpnService.prepare(MainActivity.this);
                LogFactory.writeMessage(MainActivity.this, LOG_TAG, "Startbutton clicked. Configuring VPN if needed");
                if (i != null){
                    LogFactory.writeMessage(MainActivity.this, LOG_TAG, "VPN isn't prepared yet. Showing dialog explaining the VPN");
                    dialog2 = new AlertDialog.Builder(MainActivity.this).setTitle(R.string.information).setMessage(R.string.vpn_explain)
                            .setCancelable(false).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            LogFactory.writeMessage(MainActivity.this, LOG_TAG, "Requesting VPN access", i);
                            startActivityForResult(i, 0);
                        }
                    }).show();
                    LogFactory.writeMessage(MainActivity.this, LOG_TAG, "Dialog is now being shown");
                }else{
                    LogFactory.writeMessage(MainActivity.this, LOG_TAG, "VPNService is already configured");
                    onActivityResult(0, RESULT_OK, null);
                }
            }
        });
        dns1.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(vpnRunning && doStopVPN && !wasStartedWithTasker)stopVpn();
                if (!Utils.isIP(s.toString(),settingV6)) {
                    met_dns1.setIndicatorState(MaterialEditText.IndicatorState.INCORRECT);
                } else {
                    met_dns1.setIndicatorState(MaterialEditText.IndicatorState.UNDEFINED);
                    Preferences.put(MainActivity.this, settingV6 ? "dns1-v6" :"dns1", s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        dns2.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(vpnRunning && doStopVPN && !wasStartedWithTasker)stopVpn();
                if (!Utils.isIP(s.toString(),settingV6)) {
                    met_dns2.setIndicatorState(MaterialEditText.IndicatorState.INCORRECT);
                } else {
                    met_dns2.setIndicatorState(MaterialEditText.IndicatorState.UNDEFINED);
                    Preferences.put(MainActivity.this, settingV6 ? "dns2-v6" : "dns2", s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        findViewById(R.id.settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i;
                LogFactory.writeMessage(MainActivity.this, LOG_TAG, "Opening Settings",
                        i = new Intent(MainActivity.this, SettingsActivity.class));
                startActivity(i);
            }
        });
        getSupportActionBar().setSubtitle(getString(R.string.subtitle_configuring).replace("[[x]]",settingV6 ? "Ipv6" : "Ipv4"));
        if(!Preferences.getBoolean(this, "first_run",true) && !Preferences.getBoolean(this, "rated",false) && new Random().nextInt(100) <= 8){
            LogFactory.writeMessage(this, LOG_TAG, "Showing dialog requesting rating");
            new AlertDialog.Builder(this).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    rateApp(null);
                }
            }).setNegativeButton(R.string.dont_ask_again, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Preferences.put(MainActivity.this, "rated",true);
                    dialog.cancel();
                }
            }).setNeutralButton(R.string.not_now, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            }).setMessage(R.string.rate_request_text).setTitle(R.string.rate).show();
            LogFactory.writeMessage(this, LOG_TAG, "Dialog is now being shown");
        }
        if(Preferences.getBoolean(this, "first_run", true) && API.isTaskerInstalled(this)){
            LogFactory.writeMessage(this, LOG_TAG, "Showing dialog telling the user that this app supports Tasker");
            new AlertDialog.Builder(this).setTitle(R.string.tasker_support).setMessage(R.string.app_supports_tasker_text).setPositiveButton(R.string.got_it, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            }).show();
            LogFactory.writeMessage(this, LOG_TAG, "Dialog is now being shown");
        }
        LogFactory.writeMessage(this, LOG_TAG, "Done with OnCreate");
        Preferences.put(this, "first_run", false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogFactory.writeMessage(this, LOG_TAG, "Got OnResume");
        LogFactory.writeMessage(this, LOG_TAG, "Sending ServiceStateRequest as broadcast");
        vpnRunning = API.checkVPNServiceRunning(this);
        Toast.makeText(this, "RUNNING: " + vpnRunning, Toast.LENGTH_LONG).show();
        setIndicatorState(vpnRunning);
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceStateReceiver, new IntentFilter(API.BROADCAST_SERVICE_STATUS_CHANGE));
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(API.BROADCAST_SERVICE_STATE_REQUEST));
        doStopVPN = false;
        if(settingV6){
            dns1.setText(Preferences.getString(this, "dns1", "8.8.8.8"));
            dns2.setText(Preferences.getString(this, "dns2", "8.8.4.4"));
        }else{
            dns1.setText(Preferences.getString(this, "dns1-v6", "2001:4860:4860::8888"));
            dns2.setText(Preferences.getString(this, "dns2-v6", "2001:4860:4860::8844"));
        }
        doStopVPN = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogFactory.writeMessage(this, LOG_TAG, "Got OnPause");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceStateReceiver);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        LogFactory.writeMessage(this, LOG_TAG, "Got onPostResume");
        LogFactory.writeMessage(this, LOG_TAG, "Recreating DefaultDNSDialog");
        View layout = getLayoutInflater().inflate(R.layout.dialog_default_dns, null, false);
        final ListView list = (ListView) layout.findViewById(R.id.defaultDnsDialogList);
        list.setAdapter(new DefaultDNSAdapter());
        list.setDividerHeight(0);
        defaultDnsDialog = new AlertDialog.Builder(this).setView(layout).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                LogFactory.writeMessage(MainActivity.this, LOG_TAG, "Cancelled choosing from default DNS");
            }
        }).setTitle(R.string.default_dns_title).create();
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                defaultDnsDialog.cancel();
                List<String> ips = settingV6 ? defaultDNS_V6.get(DefaultDNSKeys_V6.get(position)) : defaultDNS.get(defaultDNSKeys.get(position));
                dns1.setText(ips.get(0));
                dns2.setText(ips.get(1));
                LogFactory.writeMessage(MainActivity.this, LOG_TAG, "User chose provider from default DNS. DNS1: " + ips.get(0) + ", DNS2: " + ips.get(1));
            }
        });
        LogFactory.writeMessage(this, LOG_TAG, "DefaultDNSDialog recreated");
    }

    public void openDefaultDNSDialog(View v) {
        LogFactory.writeMessage(this, LOG_TAG, "Opening DefaultDNSDialog");
        defaultDnsDialog.show();
        LogFactory.writeMessage(this, LOG_TAG, "Dialog is now being shown");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        LogFactory.writeMessage(this, LOG_TAG, "Got OnActivityResult" ,data);
        if (requestCode == 0 && resultCode == RESULT_OK) {
            if (!vpnRunning){
                if(!Preferences.getBoolean(this, "44explained", false) && Build.VERSION.SDK_INT == 19){
                    LogFactory.writeMessage(this, LOG_TAG, "Opening Dialog explaining that this might not work on Android 4.4");
                    new AlertDialog.Builder(this).setTitle(R.string.warning).setCancelable(false).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            startVpn();
                        }
                    }).setMessage(R.string.android4_4_warning).show();
                    LogFactory.writeMessage(this, LOG_TAG, "Dialog is now being shown");
                }else{
                    startVpn();
                }
                Preferences.getBoolean(this, "44explained", true);
            }else{
                if(wasStartedWithTasker){
                    LogFactory.writeMessage(this, LOG_TAG, "Opening dialog which warns that the app was started using Tasker");
                    new AlertDialog.Builder(this).setTitle(R.string.warning).setMessage(R.string.warning_started_using_tasker). setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            LogFactory.writeMessage(MainActivity.this, LOG_TAG, "User clicked OK in the dialog warning about Tasker");
                            stopVpn();
                            dialog.cancel();
                        }
                    }).setCancelable(false).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            LogFactory.writeMessage(MainActivity.this, LOG_TAG, "User cancelled stopping DNSChanger as it was started using tasker");
                        }
                    }).show();
                    LogFactory.writeMessage(this, LOG_TAG, "Dialog is now being shown");
                }else stopVpn();
            }
        }else if(requestCode == 1 && resultCode == RESULT_OK){
            final Snackbar snackbar = Snackbar.make(wrapper, R.string.shortcut_created, Snackbar.LENGTH_INDEFINITE);
            snackbar.setAction(R.string.show, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(snackbar != null)snackbar.dismiss();
                    onBackPressed();
                }
            });
            snackbar.show();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startVpn() {
        Intent i;
        LogFactory.writeMessage(this, LOG_TAG, "Starting VPN",
                i = new Intent(this, DNSVpnService.class).putExtra("start_vpn", true).putExtra("startedWithTasker", false));
        wasStartedWithTasker = false;
        startService(i);
        vpnRunning = true;
        setIndicatorState(true);
    }

    private void stopVpn() {
        Intent i;
        LogFactory.writeMessage(this, LOG_TAG, "Stopping VPN",
                i = new Intent(this, DNSVpnService.class).putExtra("destroy", true));
        startService(i);
        stopService(new Intent(this, DNSVpnService.class));
        vpnRunning = false;
        setIndicatorState(false);
    }

    private class DefaultDNSAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return settingV6 ? defaultDNS_V6.size() : defaultDNS.size();
        }

        @Override
        public Object getItem(int position) {
            return settingV6 ? defaultDNS_V6.get(position) : defaultDNS.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = getLayoutInflater().inflate(R.layout.item_default_dns, parent, false);
            ((TextView) v.findViewById(R.id.text)).setText(settingV6 ? DefaultDNSKeys_V6.get(position) : defaultDNSKeys.get(position));
            v.setTag(getItem(position));
            return v;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(settingV6 ? R.menu.menu_main_v6 : R.menu.menu_main,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.menu_switch_ip_version){
            doStopVPN = false;
            settingV6 = !settingV6;
            invalidateOptionsMenu();
            dns1.setText(Preferences.getString(this,settingV6 ? "dns1-v6" : "dns1", settingV6 ? "2001:4860:4860::8888" : "8.8.8.8"));
            dns2.setText(Preferences.getString(this,settingV6 ? "dns2-v6" : "dns2", settingV6 ? "2001:4860:4860::8844" : "8.8.4.4"));
            dns1.setInputType(InputType.TYPE_CLASS_TEXT);
            dns2.setInputType(InputType.TYPE_CLASS_TEXT);
            getSupportActionBar().setSubtitle(getString(R.string.subtitle_configuring).replace("[[x]]",settingV6 ? "Ipv6" : "Ipv4"));
            doStopVPN = true;
        }else if(item.getItemId() == R.id.create_shortcut){
            Intent i;
            LogFactory.writeMessage(this, LOG_TAG, "User wants to create a shortcut",
                    i = new Intent(this, ConfigureActivity.class).putExtra("creatingShortcut", true));
            startActivityForResult(i,1);
        }
        return super.onOptionsItemSelected(item);
    }
}
