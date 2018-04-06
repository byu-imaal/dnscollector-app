package com.frostnerd.dnschanger.fragments;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.OrientationHelper;
import android.text.Editable;
import android.text.InputFilter;
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
import android.widget.ImageView;
import android.widget.TextView;

import com.frostnerd.dnschanger.LogFactory;
import com.frostnerd.dnschanger.R;
import com.frostnerd.dnschanger.activities.MainActivity;
import com.frostnerd.dnschanger.database.entities.DNSEntry;
import com.frostnerd.dnschanger.database.entities.IPPortPair;
import com.frostnerd.dnschanger.dialogs.NewFeaturesDialog;
import com.frostnerd.dnschanger.dialogs.VPNInfoDialog;
import com.frostnerd.dnschanger.services.DNSVpnService;
import com.frostnerd.dnschanger.util.DNSQueryUtil;
import com.frostnerd.dnschanger.util.PreferencesAccessor;
import com.frostnerd.dnschanger.util.ThemeHandler;
import com.frostnerd.dnschanger.util.Util;
import com.frostnerd.utils.design.MaterialEditText;
import com.frostnerd.utils.design.dialogs.LoadingDialog;
import com.frostnerd.dnschanger.util.Preferences;
import com.frostnerd.utils.textfilers.InputCharacterFilter;

import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
    private static final String LOG_TAG = "[MainActivity]";
    private TextView connectionText;
    private ImageView connectionImage;
    private View running_indicator;
    private boolean advancedMode;
    public boolean settingV6 = false;
    private final BroadcastReceiver serviceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LogFactory.writeMessage(requireContext(), LOG_TAG, "Received ServiceState Answer", intent);
            vpnRunning = intent.getBooleanExtra("vpn_running",false);
            wasStartedWithTasker = intent.getBooleanExtra("started_with_tasker", false);
            setIndicatorState(intent.getBooleanExtra("vpn_running",false));
        }
    };
    private View contentView;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sp, String s) {
            if(s.equals("everything_disabled")){
                boolean value = Preferences.getInstance(requireContext()).getBoolean("everything_disabled", false);
                startStopButton.setEnabled(!value);
                startStopButton.setClickable(!value);
                startStopButton.setAlpha(value ? 0.50f : 1f);
                if(!value) setIndicatorState(vpnRunning);
            }
        }
    };

    private void setIndicatorState(boolean vpnRunning) {
        LogFactory.writeMessage(requireContext(), LOG_TAG, "Changing IndicatorState to " + vpnRunning);
        if (vpnRunning) {
            connectionText.setText(R.string.running);
            if(connectionImage != null)connectionImage.setImageResource(R.drawable.ic_thumb_up);
            startStopButton.setText(R.string.stop);
            running_indicator.setBackgroundColor(Color.parseColor("#4CAF50"));
        } else {
            TypedValue typedValue = new TypedValue();
            Resources.Theme theme = requireContext().getTheme();
            theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true);
            if(PreferencesAccessor.isEverythingDisabled(requireContext()))  connectionText.setText(R.string.info_functionality_disabled);
            else connectionText.setText(R.string.not_running);
            if(connectionImage != null)connectionImage.setImageResource(R.drawable.ic_thumb_down);
            startStopButton.setText(R.string.start);
            running_indicator.setBackgroundColor(typedValue.data);
        }
        LogFactory.writeMessage(requireContext(), LOG_TAG, "IndictorState set");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        contentView = inflater.inflate(R.layout.fragment_main, container, false);
        return contentView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        startStopButton = null;
        met_dns1 = met_dns2 = null;
        dns1 = dns2 = null;
        connectionText = null;
        connectionImage = null;
        running_indicator = null;
        contentView = null;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        settingV6 = !PreferencesAccessor.isIPv4Enabled(requireContext()) || (PreferencesAccessor.isIPv6Enabled(requireContext()) && settingV6);
        setHasOptionsMenu(true);
        boolean vertical = getResources().getConfiguration().orientation == OrientationHelper.VERTICAL;
        LogFactory.writeMessage(requireContext(), LOG_TAG, "Created Activity", Util.getActivity(this).getIntent());
        LogFactory.writeMessage(requireContext(), LOG_TAG, "Setting ContentView");
        met_dns1 = (MaterialEditText) findViewById(R.id.met_dns1);
        met_dns2 = (MaterialEditText) findViewById(R.id.met_dns2);
        dns1 = (EditText) findViewById(R.id.dns1);
        dns2 = (EditText) findViewById(R.id.dns2);
        connectionImage = vertical ? null : (ImageView)findViewById(R.id.connection_status_image);
        connectionText = (TextView)findViewById(R.id.connection_status_text);
        running_indicator = findViewById(R.id.running_indicator);
        startStopButton = (Button) findViewById(R.id.startStopButton);

        if(settingV6){
            dns1.setInputType(InputType.TYPE_CLASS_TEXT);
            dns2.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent i = VpnService.prepare(requireContext());
                LogFactory.writeMessage(requireContext(), LOG_TAG, "Startbutton clicked. Configuring VPN if needed");
                if (i != null){
                    LogFactory.writeMessage(requireContext(), LOG_TAG, "VPN isn't prepared yet. Showing dialog explaining the VPN");
                    new VPNInfoDialog(requireContext(), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            startActivityForResult(i, 0);
                            LogFactory.writeMessage(requireContext(), LOG_TAG, "Requesting VPN access", i);
                        }
                    });
                    LogFactory.writeMessage(requireContext(), LOG_TAG, "Dialog is now being shown");
                }else{
                    LogFactory.writeMessage(requireContext(), LOG_TAG, "VPNService is already configured");
                    onActivityResult(0, Activity.RESULT_OK, null);
                }
            }
        });
        dns1.addTextChangedListener(new TextWatcher() {
            private String before;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                before = s.toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(this.before.equalsIgnoreCase(s.toString()))return;
                IPPortPair pair = Util.validateInput(s.toString(), settingV6, false,
                        PreferencesAccessor.isLoopbackAllowed(requireContext()), 53);
                if (pair == null || (pair.getPort() != 53 && !advancedMode)) {
                    met_dns1.setIndicatorState(MaterialEditText.IndicatorState.INCORRECT);
                } else {
                    met_dns1.setIndicatorState(MaterialEditText.IndicatorState.UNDEFINED);
                    if (settingV6) PreferencesAccessor.Type.DNS1_V6.saveDNSPair(requireContext(), pair);
                    else PreferencesAccessor.Type.DNS1.saveDNSPair(requireContext(), pair);
                    setEditTextLabel();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        dns2.addTextChangedListener(new TextWatcher() {
            private String before;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                before = s.toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(this.before.equalsIgnoreCase(s.toString()))return;
                IPPortPair pair = Util.validateInput(s.toString(), settingV6, true,
                        PreferencesAccessor.isLoopbackAllowed(requireContext()), 53);
                if (pair == null || (pair != IPPortPair.getEmptyPair() && pair.getPort() != 53 && !advancedMode)) {
                    met_dns2.setIndicatorState(MaterialEditText.IndicatorState.INCORRECT);
                } else {
                    met_dns2.setIndicatorState(MaterialEditText.IndicatorState.UNDEFINED);
                    if (settingV6) PreferencesAccessor.Type.DNS2_V6.saveDNSPair(requireContext(), pair);
                    else PreferencesAccessor.Type.DNS2.saveDNSPair(requireContext(), pair);
                    setEditTextLabel();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        setEditTextLabel();
        if(NewFeaturesDialog.shouldShowDialog(requireContext())){
            new NewFeaturesDialog(requireContext()).show();
        }
        LogFactory.writeMessage(requireContext(), LOG_TAG, "Done with OnCreate");
    }

    private void setEditTextLabel(){
        String label1 = "DNS 1", label2 = "DNS 2";
        if(settingV6){
            DNSEntry entry;
            if((entry = PreferencesAccessor.Type.DNS1_V6.findMatchingDatabaseEntry(requireContext())) != null)
                label1 += " (" + entry.getShortName() + ")";
            if((entry = PreferencesAccessor.Type.DNS2_V6.findMatchingDatabaseEntry(requireContext())) != null)
                label2 += " (" + entry.getShortName() + ")";
        }else{
            DNSEntry entry;
            if((entry = PreferencesAccessor.Type.DNS1.findMatchingDatabaseEntry(requireContext())) != null)
                label1 += " (" + entry.getShortName() + ")";
            if((entry = PreferencesAccessor.Type.DNS2.findMatchingDatabaseEntry(requireContext())) != null)
                label2 += " (" + entry.getShortName() + ")";
        }
        met_dns1.setLabelText(label1);
        met_dns2.setLabelText(label2);
    }

    private void setEditTextState(){
        boolean customPorts = PreferencesAccessor.areCustomPortsEnabled(requireContext());
        if(settingV6 || customPorts){
            dns1.setInputType(InputType.TYPE_CLASS_TEXT);
            dns2.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        if(!settingV6){
            InputFilter filter = new InputCharacterFilter(customPorts ?
                    Pattern.compile("[0-9.:]") : Pattern.compile("[0-9.]"));
            dns1.setFilters(new InputFilter[]{filter});
            dns2.setFilters(new InputFilter[]{filter});
            IPPortPair p1 = PreferencesAccessor.Type.DNS1.getPair(requireContext()),
                    p2 = PreferencesAccessor.Type.DNS2.getPair(requireContext());
            dns1.setText(p1.formatForTextfield(customPorts));
            dns2.setText(p2.formatForTextfield(customPorts));
        }else{
            InputFilter filter = new InputCharacterFilter(customPorts ?
                    Pattern.compile("[0-9:a-f\\[\\]]") : Pattern.compile("[0-9:a-f]"));
            dns1.setFilters(new InputFilter[]{filter});
            dns2.setFilters(new InputFilter[]{filter});
            IPPortPair p1 = PreferencesAccessor.Type.DNS1_V6.getPair(requireContext()),
                    p2 = PreferencesAccessor.Type.DNS2_V6.getPair(requireContext());
            dns1.setText(p1.formatForTextfield(customPorts));
            dns2.setText(p2.formatForTextfield(customPorts));
        }
    }

    private View findViewById(@IdRes int id){
        return contentView.findViewById(id);
    }

    @Override
    public void onResume() {
        super.onResume();
        advancedMode = PreferencesAccessor.isRunningInAdvancedMode(requireContext());
        Preferences.getDefaultPreferences(requireContext()).registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        settingV6 = !PreferencesAccessor.isIPv4Enabled(requireContext()) || (PreferencesAccessor.isIPv6Enabled(requireContext()) && settingV6);
        LogFactory.writeMessage(requireContext(), LOG_TAG, "Got OnResume");
        LogFactory.writeMessage(requireContext(), LOG_TAG, "Sending ServiceStateRequest as broadcast");
        vpnRunning = Util.isServiceRunning(requireContext());
        if(PreferencesAccessor.isEverythingDisabled(requireContext())){
            startStopButton.setEnabled(false);
            startStopButton.setClickable(false);
            startStopButton.setAlpha(0.50f);
            connectionText.setText(R.string.info_functionality_disabled);
        }else{
            startStopButton.setEnabled(true);
            startStopButton.setClickable(true);
            startStopButton.setAlpha(1f);
            setIndicatorState(vpnRunning);
        }
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(serviceStateReceiver, new IntentFilter(Util.BROADCAST_SERVICE_STATUS_CHANGE));
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(new Intent(Util.BROADCAST_SERVICE_STATE_REQUEST));
        setEditTextState();
        ((AppCompatActivity)requireContext()).getSupportActionBar().setSubtitle(getString(R.string.subtitle_configuring).replace("[[x]]",settingV6 ? "Ipv6" : "Ipv4"));
        Util.getActivity(this).invalidateOptionsMenu();
        Util.getActivity(this).getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    @Override
    public void onPause() {
        super.onPause();
        LogFactory.writeMessage(requireContext(), LOG_TAG, "Got OnPause");
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(serviceStateReceiver);
        Preferences.getDefaultPreferences(requireContext()).unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        LogFactory.writeMessage(requireContext(), LOG_TAG, "Got OnActivityResult" ,data);
        if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
            if (!vpnRunning){
                if(!Preferences.getInstance(requireContext()).getBoolean("44explained", false) && Build.VERSION.SDK_INT == 19){
                    LogFactory.writeMessage(requireContext(), LOG_TAG, "Opening Dialog explaining that this might not work on Android 4.4");
                    new AlertDialog.Builder(requireContext(), ThemeHandler.getDialogTheme(requireContext())).setTitle(R.string.warning).setCancelable(false).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            startVpn();
                        }
                    }).setMessage(R.string.android4_4_warning).show();
                    LogFactory.writeMessage(requireContext(), LOG_TAG, "Dialog is now being shown");
                }else{
                    startVpn();
                }
                Preferences.getInstance(requireContext()).getBoolean("44explained", true);
            }else{
                if(wasStartedWithTasker){
                    LogFactory.writeMessage(requireContext(), LOG_TAG, "Opening dialog which warns that the app was started using Tasker");
                    new AlertDialog.Builder(requireContext(),ThemeHandler.getDialogTheme(requireContext())).setTitle(R.string.warning).setMessage(R.string.warning_started_using_tasker). setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            LogFactory.writeMessage(requireContext(), LOG_TAG, "User clicked OK in the dialog warning about Tasker");
                            stopVpn();
                            dialog.cancel();
                        }
                    }).setCancelable(false).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            LogFactory.writeMessage(requireContext(), LOG_TAG, "User cancelled stopping DNSChanger as it was started using tasker");
                        }
                    }).show();
                    LogFactory.writeMessage(requireContext(), LOG_TAG, "Dialog is now being shown");
                }else stopVpn();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startVpn() {
        if(PreferencesAccessor.checkConnectivityOnStart(requireContext())){
            final LoadingDialog dialog = new LoadingDialog(requireContext(), R.string.checking_connectivity, R.string.dialog_connectivity_description);
            dialog.show();
            checkDNSReachability(new DNSReachabilityCallback() {
                @Override
                public void checkFinished(List<IPPortPair> unreachable, List<IPPortPair> reachable) {
                    dialog.dismiss();
                    if(unreachable.size() == 0){
                        ((MainActivity)requireContext()).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                start();
                            }
                        });
                    }else{
                        String _text = getString(R.string.no_connectivity_warning_text);
                        StringBuilder builder = new StringBuilder();
                        _text = _text.replace("[x]", unreachable.size() + reachable.size() + "");
                        _text = _text.replace("[y]", unreachable.size() + "");
                        boolean customPorts = PreferencesAccessor.areCustomPortsEnabled(requireContext());
                        for(IPPortPair p: unreachable)builder.append("- ").append(p.formatForTextfield(customPorts)).append("\n");
                        _text = _text.replace("[servers]", builder.toString());
                        final String text = _text;
                        ((MainActivity)requireContext()).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new AlertDialog.Builder(requireContext(), ThemeHandler.getDialogTheme(requireContext()))
                                        .setTitle(R.string.warning).setCancelable(true).setPositiveButton(R.string.start, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        start();
                                    }
                                }).setNegativeButton(R.string.cancel, null).setMessage(text).show();
                            }
                        });
                    }
                }

                private void start(){
                    Intent i;
                    LogFactory.writeMessage(requireContext(), LOG_TAG, "Starting VPN",
                            i = DNSVpnService.getStartVPNIntent(requireContext()));
                    wasStartedWithTasker = false;
                    Util.startService(requireContext(), i);
                    vpnRunning = true;
                    setIndicatorState(true);
                }
            });
        }else{
            Intent i;
            LogFactory.writeMessage(requireContext(), LOG_TAG, "Starting VPN",
                    i = DNSVpnService.getStartVPNIntent(requireContext()));
            wasStartedWithTasker = false;
            Util.startService(requireContext(), i);
            vpnRunning = true;
            setIndicatorState(true);
        }
    }

    private void stopVpn() {
        Intent i;
        LogFactory.writeMessage(requireContext(), LOG_TAG, "Stopping VPN",
                i = DNSVpnService.getDestroyIntent(requireContext()));
        requireContext().startService(i);
        vpnRunning = false;
        setIndicatorState(false);
    }

    public void toggleVPN(){
        if (vpnRunning){
            stopVpn();
        }else startVpn();
    }

    public boolean toggleCurrentInputFocus(){
        if(dns1.hasFocus()){
            dns2.requestFocus();
        }else if(dns2.hasFocus()){
            dns1.requestFocus();
        }else return false;
        return true;
    }

    public void checkDNSReachability(final DNSReachabilityCallback callback){
        List<IPPortPair> servers = PreferencesAccessor.getAllDNSPairs(requireContext(), true);
        callback.setServers(servers.size());
        for(final IPPortPair pair: servers){
            DNSQueryUtil.runAsyncDNSQuery(pair, "frostnerd.com.", PreferencesAccessor.sendDNSOverTCP(requireContext()), Type.A, DClass.IN, new Util.DNSQueryResultListener() {
                @Override
                public void onSuccess(Message response) {
                    callback.checkProgress(pair, true);
                }

                @Override
                public void onError(@Nullable Exception e) {
                    callback.checkProgress(pair, false);
                }
            }, 1);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu,inflater);
        inflater.inflate(PreferencesAccessor.isIPv6Enabled(requireContext()) ? (PreferencesAccessor.isIPv4Enabled(requireContext()) ? ((settingV6 ? R.menu.menu_main_v6 : R.menu.menu_main)) : R.menu.menu_main_no_ipv6) : R.menu.menu_main_no_ipv6,menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.menu_switch_ip_version){
            settingV6 = !settingV6;
            Util.getActivity(this).invalidateOptionsMenu();
            setEditTextState();
            ((AppCompatActivity)requireContext()).getSupportActionBar().setSubtitle(getString(R.string.subtitle_configuring).replace("[[x]]",settingV6 ? "Ipv6" : "Ipv4"));
        }
        return super.onOptionsItemSelected(item);
    }

    public static abstract class DNSReachabilityCallback{
        private final List<IPPortPair> unreachable = new ArrayList<>();
        private final List<IPPortPair> reachable = new ArrayList<>();
        private int servers;

        public abstract void checkFinished(List<IPPortPair> unreachable, List<IPPortPair> reachable);

        public final void checkProgress(IPPortPair server, boolean reachable){
            if(!reachable)unreachable.add(server);
            else this.reachable.add(server);
            if(this.unreachable.size() + this.reachable.size() >= servers)checkFinished(unreachable, this.reachable);
        }

        void setServers(int servers){
            this.servers = servers;
        }

    }
}
