package com.frostnerd.dnschanger.fragments;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.ArraySet;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.OrientationHelper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.frostnerd.dnschanger.API.API;
import com.frostnerd.dnschanger.API.ThemeHandler;
import com.frostnerd.dnschanger.LogFactory;
import com.frostnerd.dnschanger.R;
import com.frostnerd.dnschanger.activities.MainActivity;
import com.frostnerd.dnschanger.activities.SettingsActivity;
import com.frostnerd.dnschanger.dialogs.DefaultDNSDialog;
import com.frostnerd.dnschanger.services.ConnectivityBackgroundService;
import com.frostnerd.dnschanger.services.DNSVpnService;
import com.frostnerd.dnschanger.tasker.ConfigureActivity;
import com.frostnerd.utils.design.MaterialEditText;
import com.frostnerd.utils.general.IntentUtil;
import com.frostnerd.utils.general.Utils;
import com.frostnerd.utils.networking.NetworkUtil;
import com.frostnerd.utils.preferences.Preferences;

import java.util.Arrays;
import java.util.Random;

/**
 * Copyright Daniel Wolf 2017
 * All rights reserved.
 * <p>
 * development@frostnerd.com
 */
public class MainFragment extends Fragment {
    private Button startStopButton;
    private boolean vpnRunning, wasStartedWithTasker = false;
    private MaterialEditText met_dns1, met_dns2;
    public EditText dns1, dns2;
    private boolean doStopVPN = true;
    private static final String LOG_TAG = "[MainActivity]";
    private TextView connectionText;
    private ImageView connectionImage;
    private View running_indicator;
    private View wrapper;
    public boolean settingV6 = false;
    private final int REQUEST_SETTINGS = 13;
    private AlertDialog dialog2;
    private BroadcastReceiver serviceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LogFactory.writeMessage(getActivity(), LOG_TAG, "Received ServiceState Answer", intent);
            vpnRunning = intent.getBooleanExtra("vpn_running",false);
            wasStartedWithTasker = intent.getBooleanExtra("started_with_tasker", false);
            setIndicatorState(intent.getBooleanExtra("vpn_running",false));
        }
    };
    private View contentView;

    private void setIndicatorState(boolean vpnRunning) {
        LogFactory.writeMessage(getActivity(), LOG_TAG, "Changing IndicatorState to " + vpnRunning);
        if (vpnRunning) {
            int color = Color.parseColor("#42A5F5");
            connectionText.setText(R.string.running);
            if(connectionImage != null)connectionImage.setImageResource(R.drawable.ic_thumb_up);
            startStopButton.setText(R.string.stop);
            running_indicator.setBackgroundColor(Color.parseColor("#4CAF50"));
        } else {
            TypedValue typedValue = new TypedValue();
            Resources.Theme theme = getActivity().getTheme();
            theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true);
            connectionText.setText(R.string.not_running);
            if(connectionImage != null)connectionImage.setImageResource(R.drawable.ic_thumb_down);
            startStopButton.setText(R.string.start);
            running_indicator.setBackgroundColor(typedValue.data);
        }
        LogFactory.writeMessage(getActivity(), LOG_TAG, "IndictorState set");
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        contentView = inflater.inflate(R.layout.fragment_main, container, false);
        return contentView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        settingV6 = !API.isIPv4Enabled(getActivity()) || (API.isIPv6Enabled(getActivity()) && settingV6);
        boolean vertical = getResources().getConfiguration().orientation == OrientationHelper.VERTICAL;
        LogFactory.writeMessage(getActivity(), LOG_TAG, "Created Activity", getActivity().getIntent());
        API.updateTiles(getActivity());
        LogFactory.writeMessage(getActivity(), LOG_TAG, "Launching ConnectivityBackgroundService");
        getActivity().startService(new Intent(getActivity(), ConnectivityBackgroundService.class));
        LogFactory.writeMessage(getActivity(), LOG_TAG, "Setting ContentView");
        met_dns1 = (MaterialEditText) findViewById(R.id.met_dns1);
        met_dns2 = (MaterialEditText) findViewById(R.id.met_dns2);
        dns1 = (EditText) findViewById(R.id.dns1);
        dns2 = (EditText) findViewById(R.id.dns2);
        connectionImage = vertical ? null : (ImageView)findViewById(R.id.connection_status_image);
        connectionText = (TextView)findViewById(R.id.connection_status_text);
        wrapper = findViewById(R.id.activity_main);
        running_indicator = findViewById(R.id.running_indicator);
        dns1.setText(Preferences.getString(getActivity(),settingV6 ? "dns1-v6" : "dns1", settingV6 ? "2001:4860:4860::8888" : "8.8.8.8"));
        dns2.setText(Preferences.getString(getActivity(),settingV6 ? "dns1-v6" : "dns1", settingV6 ? "2001:4860:4860::8844" : "8.8.4.4"));
        if(settingV6){
            dns1.setInputType(InputType.TYPE_CLASS_TEXT);
            dns2.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        startStopButton = (Button) findViewById(R.id.startStopButton);
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent i = VpnService.prepare(getActivity());
                LogFactory.writeMessage(getActivity(), LOG_TAG, "Startbutton clicked. Configuring VPN if needed");
                if (i != null){
                    LogFactory.writeMessage(getActivity(), LOG_TAG, "VPN isn't prepared yet. Showing dialog explaining the VPN");
                    dialog2 = new AlertDialog.Builder(getActivity(),ThemeHandler.getDialogTheme(getActivity())).setTitle(R.string.information).setMessage(R.string.vpn_explain)
                            .setCancelable(false).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                    LogFactory.writeMessage(getActivity(), LOG_TAG, "Requesting VPN access", i);
                                    startActivityForResult(i, 0);
                                }
                            }).show();
                    LogFactory.writeMessage(getActivity(), LOG_TAG, "Dialog is now being shown");
                }else{
                    LogFactory.writeMessage(getActivity(), LOG_TAG, "VPNService is already configured");
                    onActivityResult(0, Activity.RESULT_OK, null);
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
                if (!NetworkUtil.isAssignableAddress(s.toString(),settingV6,false)) {
                    met_dns1.setIndicatorState(MaterialEditText.IndicatorState.INCORRECT);
                } else {
                    met_dns1.setIndicatorState(MaterialEditText.IndicatorState.UNDEFINED);
                    Preferences.put(getActivity(), settingV6 ? "dns1-v6" :"dns1", s.toString());
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
                if (!NetworkUtil.isAssignableAddress(s.toString(),settingV6, true)) {
                    met_dns2.setIndicatorState(MaterialEditText.IndicatorState.INCORRECT);
                } else {
                    met_dns2.setIndicatorState(MaterialEditText.IndicatorState.UNDEFINED);
                    Preferences.put(getActivity(), settingV6 ? "dns2-v6" : "dns2", s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(getString(R.string.subtitle_configuring).replace("[[x]]",settingV6 ? "Ipv6" : "Ipv4"));
        int random = new Random().nextInt(100), launches = Preferences.getInteger(getActivity(), "launches", 0);
        Preferences.put(getActivity(), "launches", launches+1);
        if(!Preferences.getBoolean(getActivity(), "first_run",true) && !Preferences.getBoolean(getActivity(), "rated",false) && random <= (launches >= 3 ? 8 : 3)){
            LogFactory.writeMessage(getActivity(), LOG_TAG, "Showing dialog requesting rating");
            new AlertDialog.Builder(getActivity(),ThemeHandler.getDialogTheme(getActivity())).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ((MainActivity)getActivity()).rateApp();
                }
            }).setNegativeButton(R.string.dont_ask_again, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Preferences.put(getActivity(), "rated",true);
                    dialog.cancel();
                }
            }).setNeutralButton(R.string.not_now, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            }).setMessage(R.string.rate_request_text).setTitle(R.string.rate).show();
            LogFactory.writeMessage(getActivity(), LOG_TAG, "Dialog is now being shown");
        }
        if(Preferences.getBoolean(getActivity(), "first_run", true) && API.isTaskerInstalled(getActivity())){
            LogFactory.writeMessage(getActivity(), LOG_TAG, "Showing dialog telling the user that this app supports Tasker");
            new AlertDialog.Builder(getActivity(),ThemeHandler.getDialogTheme(getActivity())).setTitle(R.string.tasker_support).setMessage(R.string.app_supports_tasker_text).setPositiveButton(R.string.got_it, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            }).show();
            LogFactory.writeMessage(getActivity(), LOG_TAG, "Dialog is now being shown");
        }
        if(Preferences.getBoolean(getActivity(), "first_run", true)) Preferences.put(getActivity(), "excluded_apps", new ArraySet<>(Arrays.asList(getResources().getStringArray(R.array.default_blacklist))));
        API.updateAppShortcuts(getActivity());
        new Thread(new Runnable() {
            @Override
            public void run() {
                API.getDBHelper(getActivity()).getReadableDatabase();
            }
        }).start();
        LogFactory.writeMessage(getActivity(), LOG_TAG, "Done with OnCreate");
        Preferences.put(getActivity(), "first_run", false);
    }

    private View findViewById(@IdRes int id){
        return contentView.findViewById(id);
    }

    @Override
    public void onResume() {
        super.onResume();
        settingV6 = !API.isIPv4Enabled(getActivity()) || (API.isIPv6Enabled(getActivity()) && settingV6);
        LogFactory.writeMessage(getActivity(), LOG_TAG, "Got OnResume");
        LogFactory.writeMessage(getActivity(), LOG_TAG, "Sending ServiceStateRequest as broadcast");
        vpnRunning = API.isServiceRunning(getActivity());
        setIndicatorState(vpnRunning);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(serviceStateReceiver, new IntentFilter(API.BROADCAST_SERVICE_STATUS_CHANGE));
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(new Intent(API.BROADCAST_SERVICE_STATE_REQUEST));
        doStopVPN = false;
        if(!settingV6){
            dns1.setText(Preferences.getString(getActivity(), "dns1", "8.8.8.8"));
            dns2.setText(Preferences.getString(getActivity(), "dns2", "8.8.4.4"));
        }else{
            dns1.setText(Preferences.getString(getActivity(), "dns1-v6", "2001:4860:4860::8888"));
            dns2.setText(Preferences.getString(getActivity(), "dns2-v6", "2001:4860:4860::8844"));
            dns1.setInputType(InputType.TYPE_CLASS_TEXT);
            dns2.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(getString(R.string.subtitle_configuring).replace("[[x]]",settingV6 ? "Ipv6" : "Ipv4"));
        getActivity().invalidateOptionsMenu();
        doStopVPN = true;
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    @Override
    public void onPause() {
        super.onPause();
        LogFactory.writeMessage(getActivity(), LOG_TAG, "Got OnPause");
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(serviceStateReceiver);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        LogFactory.writeMessage(getActivity(), LOG_TAG, "Got OnActivityResult" ,data);
        if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
            if (!vpnRunning){
                if(!Preferences.getBoolean(getActivity(), "44explained", false) && Build.VERSION.SDK_INT == 19){
                    LogFactory.writeMessage(getActivity(), LOG_TAG, "Opening Dialog explaining that this might not work on Android 4.4");
                    new AlertDialog.Builder(getActivity(), ThemeHandler.getDialogTheme(getActivity())).setTitle(R.string.warning).setCancelable(false).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            startVpn();
                        }
                    }).setMessage(R.string.android4_4_warning).show();
                    LogFactory.writeMessage(getActivity(), LOG_TAG, "Dialog is now being shown");
                }else{
                    startVpn();
                }
                Preferences.getBoolean(getActivity(), "44explained", true);
            }else{
                if(wasStartedWithTasker){
                    LogFactory.writeMessage(getActivity(), LOG_TAG, "Opening dialog which warns that the app was started using Tasker");
                    new AlertDialog.Builder(getActivity(),ThemeHandler.getDialogTheme(getActivity())).setTitle(R.string.warning).setMessage(R.string.warning_started_using_tasker). setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            LogFactory.writeMessage(getActivity(), LOG_TAG, "User clicked OK in the dialog warning about Tasker");
                            stopVpn();
                            dialog.cancel();
                        }
                    }).setCancelable(false).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            LogFactory.writeMessage(getActivity(), LOG_TAG, "User cancelled stopping DNSChanger as it was started using tasker");
                        }
                    }).show();
                    LogFactory.writeMessage(getActivity(), LOG_TAG, "Dialog is now being shown");
                }else stopVpn();
            }
        }else if(requestCode == 1 && resultCode == Activity.RESULT_OK){
            final Snackbar snackbar = Snackbar.make(wrapper, R.string.shortcut_created, Snackbar.LENGTH_INDEFINITE);
            snackbar.setAction(R.string.show, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    snackbar.dismiss();
                    Utils.goToLauncher(getActivity());
                }
            });
            snackbar.show();
        }else if(requestCode == REQUEST_SETTINGS && resultCode == Activity.RESULT_FIRST_USER){
            if(IntentUtil.checkExtra("themeupdated",data))IntentUtil.restartActivity(getActivity());
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startVpn() {
        Intent i;
        LogFactory.writeMessage(getActivity(), LOG_TAG, "Starting VPN",
                i = DNSVpnService.getStartVPNIntent(getActivity()));
        wasStartedWithTasker = false;
        getActivity().startService(i);
        vpnRunning = true;
        setIndicatorState(true);
    }

    private void stopVpn() {
        Intent i;
        LogFactory.writeMessage(getActivity(), LOG_TAG, "Stopping VPN",
                i = DNSVpnService.getDestroyIntent(getActivity()));
        getActivity().startService(i);
        getActivity().stopService(new Intent(getActivity(), DNSVpnService.class));
        vpnRunning = false;
        setIndicatorState(false);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu,inflater);
        inflater.inflate(API.isIPv6Enabled(getActivity()) ? (API.isIPv4Enabled(getActivity()) ? ((settingV6 ? R.menu.menu_main_v6 : R.menu.menu_main)) : R.menu.menu_main_no_ipv6) : R.menu.menu_main_no_ipv6,menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.menu_switch_ip_version){
            doStopVPN = false;
            settingV6 = !settingV6;
            getActivity().invalidateOptionsMenu();
            dns1.setText(Preferences.getString(getActivity(),settingV6 ? "dns1-v6" : "dns1", settingV6 ? "2001:4860:4860::8888" : "8.8.8.8"));
            dns2.setText(Preferences.getString(getActivity(),settingV6 ? "dns2-v6" : "dns2", settingV6 ? "2001:4860:4860::8844" : "8.8.4.4"));
            dns1.setInputType(InputType.TYPE_CLASS_TEXT);
            dns2.setInputType(InputType.TYPE_CLASS_TEXT);
            ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(getString(R.string.subtitle_configuring).replace("[[x]]",settingV6 ? "Ipv6" : "Ipv4"));
            doStopVPN = true;
        }else if(item.getItemId() == R.id.create_shortcut){
            Intent i;
            LogFactory.writeMessage(getActivity(), LOG_TAG, "User wants to create a shortcut",
                    i = new Intent(getActivity(), ConfigureActivity.class).putExtra("creatingShortcut", true));
            startActivityForResult(i,1);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Toast.makeText(getActivity(), "landscape", Toast.LENGTH_SHORT).show();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            Toast.makeText(getActivity(), "portrait", Toast.LENGTH_SHORT).show();
        }
    }
}
