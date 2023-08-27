package org.lineageos.setupwizard;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.BaseAdapter;
import android.widget.Switch;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.net.Uri;
import android.net.ConnectivityManager;

import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.Exception;
import java.lang.StringBuilder;
import java.util.*;
import java.util.function.Consumer;
import java.io.InputStreamReader;

import org.lineageos.setupwizard.FDroidInstallerActivity;

import org.json.JSONObject;

import com.google.android.setupcompat.util.SystemBarHelper;
import android.util.Log;

import org.lineageos.setupwizard.R;

import static org.lineageos.setupwizard.SetupWizardApp.KEY_SEND_METRICS;

public class FDroidInstallerReceiver extends BroadcastReceiver {
        public static final String TAG = FDroidInstallerReceiver.class.getSimpleName();
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "Apps Installed Broadcast Received");

            if (!FDroidInstallerActivity.ACTION_INSTALL_COMPLETE.equals(intent.getAction())) {
                return;
            }

            int result = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);

            String error = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

            Log.d(TAG, "PackageInstallerCallback: result=" + result
                    + " packageName=" + packageName);
            switch (result) {
                case PackageInstaller.STATUS_SUCCESS: {
                    Toast.makeText(context, "Apps Installed.", Toast.LENGTH_LONG).show();
                } break;
                default: {
                    Log.e(TAG, "Install failed.");
                    Log.v(TAG, error);
                    return;
                }
            }
        }
}

