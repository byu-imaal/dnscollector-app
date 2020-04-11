package com.frostnerd.dnschanger.services;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.frostnerd.dnschanger.LogFactory;
import com.frostnerd.dnschanger.R;
import com.frostnerd.dnschanger.util.Util;

/*
 * Copyright (C) 2020 Daniel Wolf (Ch4t4r)
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
public class ConnectivityCheckRestartService extends Service {
    private static final String LOG_TAG = "[ConnectivityCheckRestartService]";
    private NotificationCompat.Builder notificationBuilder;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationBuilder = new NotificationCompat.Builder(this, Util.createNotificationChannel(this, true));
        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher);
        notificationBuilder.setOngoing(true);
        notificationBuilder.setContentTitle(getString(R.string.notification_connectivity_service));
        notificationBuilder.setContentText(getString(R.string.notification_connectivity_service_message));
        notificationBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
        startForeground(1286, notificationBuilder.build());
        LogFactory.writeMessage(this, LOG_TAG, "Service created.");
        stopForeground(false);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(1286);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1286, notificationBuilder.build());
        LogFactory.writeMessage(this, LOG_TAG, "Start command received.");
        Util.runBackgroundConnectivityCheck(this, false);
        stopForeground(false);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(1286);
        stopSelf();
        LogFactory.writeMessage(this, LOG_TAG, "Stopping self.");
        return START_NOT_STICKY;
    }
}
