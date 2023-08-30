package org.lineageos.setupwizard;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.settings.SettingsEnums;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInfo;
import android.nfc.NfcAdapter;
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
import android.net.ConnectivitySettingsManager;
import android.telecom.TelecomManager;
import android.app.role.RoleManager;
import android.telecom.DefaultDialerManager;


import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.Exception;
import java.lang.StringBuilder;
import java.lang.Process;
import java.lang.Runtime;
import java.util.*;
import java.util.function.Consumer;
import java.io.InputStreamReader;

import org.json.JSONObject;

import com.google.android.setupcompat.util.SystemBarHelper;
import android.util.Log;

import org.lineageos.setupwizard.R;

import lineageos.providers.LineageSettings;
import lineageos.trust.TrustInterface;

import static org.lineageos.setupwizard.SetupWizardApp.KEY_SEND_METRICS;
import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;

public class FDroidInstallerActivity extends BaseSetupWizardActivity {

   ArrayList<Thread> threads = new ArrayList<Thread>();
   public class GetJSON implements Runnable {
       public volatile String json_data;
       public String getJSONData() {
         return json_data;
       }
       public void run(){};
   }

    public class DownloadApp implements Runnable {
        App app = null;
        public DownloadApp(App app) {
            this.app = app;
        }
        public void run() {
            int count = 0;
            try {
                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                URL url = new URL(app.url);
                InputStream input = new BufferedInputStream(url.openStream(), 8192);
                Log.v(TAG, "Downloading App '" + app.fileName + "'");
                String fileName = downloads + "/" + app.fileName;
                OutputStream output = new FileOutputStream(fileName);
                byte data[] = new byte[1024];

                long total = 0;

                while ((count = input.read(data)) != -1) {
                    total += count;

                    // writing data to file
                    output.write(data, 0, count);
                }

                output.flush();

                output.close();
                input.close();

                if(!new File(fileName).exists())
                {
                    run();
                }

                return;
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static final String ACTION_INSTALL_COMPLETE = "org.lineageos.setupwizard.INSTALL_COMPLETE";

    public class InstallApp implements Runnable {
        App app = null;
        Context context = null;
        public InstallApp(App app, Context context) {
            this.context = context;
            this.app = app;
        }
        private IntentSender createIntentSender(Context context, int sessionId, String complete) {
            Intent intent = new Intent(complete);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE);
            return pendingIntent.getIntentSender();
        }
        public void run() {
            DownloadApp download = new DownloadApp(app);
            Thread t = new Thread(download);
            t.start();

            while(t.isAlive()) {
                //Do Nothing
            }

            try {
                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloads + app.fileName);
                Log.v(TAG, "Installing App '" + app.fileName + "'");

                InputStream input = new FileInputStream(file);
                PackageInstaller.Session session = null;
                PackageInstaller installer = context.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                                                           PackageInstaller.SessionParams.MODE_FULL_INSTALL);

                PackageInfo info = context.getPackageManager().getPackageArchiveInfo(file.toString(), 1);
                String packageName = "";
                if (info != null) {
                    ApplicationInfo appInfo = info.applicationInfo;
                    packageName = appInfo.packageName;
                }

                params.setAppPackageName(packageName);

//                Log.v(TAG, "Package name '" + packageName + "'");

                int sessionId = installer.createSession(params);
                session = installer.openSession(sessionId);

                OutputStream output = session.openWrite(file.getName(), 0, -1);
                final byte[] buffer = new byte[65536];
                int bytes_read;
                while((bytes_read = input.read(buffer)) != -1){
                    output.write(buffer, 0, bytes_read);
                }

                session.fsync(output);
                input.close();
                output.close();
                
                session.commit(createIntentSender(context, sessionId, ACTION_INSTALL_COMPLETE));

                file.delete();
            } catch(Exception e) {
                Log.v(TAG, e.getMessage());
                e.printStackTrace();
            }
        }
    }

   public class App {
        String package_name = "";
        boolean isWizardApp = false;
        boolean isRecommendedApp = false;
        String url = "";
        boolean state = true;
        long id = new Random().nextLong();
        boolean installed = false;
        String fileName = "";
        String mimeCode = "";
   }

   public ArrayList<App> apps = new ArrayList<App>();

    GetJSON getJSON = new GetJSON() {
        public void run() {
              try {
                  URL url = new URL("https://appstore.privacysociety.org/fdroid/repo/index-v2.json"); 
                  HttpURLConnection conn=(HttpURLConnection) url.openConnection();

                  BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                  StringBuilder sb = new StringBuilder();
                  String str = "";
                  while ((str = in.readLine()) != null) {
                      sb.append(str);
                  }
                  in.close();
                  json_data = sb.toString();
              } catch(Exception e) { 
                  e.printStackTrace();
              }
    }};

    public class AppItemViewHolder {
        View base;
        Switch mSwitch;
        public AppItemViewHolder(View base) {
            this.base = base;
        }
        public Switch getSwitch()  {
            if(mSwitch == null) {
                mSwitch = (Switch) base.findViewById(R.id.app_switch);
            }
            return mSwitch;
        }
    }

    public class AppAdapter extends BaseAdapter {
        List<App> apps;
        Context context;
        View tmpView;
        AppItemViewHolder mHolder;
        App tmpItem;
        public AppAdapter(List<App> apps, Context context) {
            this.apps = apps;
            this.context = context;
        }
        @Override
        public int getCount() {
            return apps == null ? 0 : apps.size();
        }

        @Override
        public App getItem(int i) {
            return apps == null ? null : apps.get(i);
        }

        @Override
        public long getItemId(int i) {
            return apps == null ? 0 : apps.get(i).id;
        }

        @Override
        public View getView(final int i, View view, ViewGroup viewGroup) {

            tmpItem = apps.get(i);

            if (view == null) {
                LayoutInflater inflater = LayoutInflater.from(context);
                tmpView = inflater.inflate(R.layout.fdroid_list_row, null, false);
                mHolder = new AppItemViewHolder(tmpView);
                tmpView.setTag(mHolder);
            }
            else {
                tmpView = view;
                mHolder = (AppItemViewHolder) view.getTag();
            }

            mHolder.getSwitch().setText(tmpItem.package_name);
//            mHolder.getSwitch().setChecked(tmpItem.state);
            mHolder.getSwitch().setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    apps.get(i).state = b;
                }
            });

            return tmpView;
        }
    }

  public static final String TAG = FDroidInstallerActivity.class.getSimpleName();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      setNextAllowed(false);

      applyMiscSetting();

      ConnectivityManager conManager = (ConnectivityManager) getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
      var network = conManager.getNetworkCapabilities(conManager.getActiveNetwork());
      if(network == null) {
          setNextAllowed(false);
          Toast.makeText(this, "No Internet Connection Available.", Toast.LENGTH_LONG).show();        
          return;
      }

      downloadAndProcessJSON();
      displayRecommendedApps();
      SystemBarHelper.setBackButtonVisible(getWindow(), true);
  }

    protected void onResume() {
//        setNextAllowed(true);
        super.onResume();
    }

  private void downloadAndProcessJSON() {
      Thread t = new Thread(getJSON);
      t.start();

      while(t.isAlive()) {
        //Do Nothing, wait
          setNextAllowed(false);
      }

      setNextAllowed(true);
      String json_data = getJSON.getJSONData();
      buildAppList(json_data);

}

  private void installApp(App app) {
        Thread appThread = new Thread(new InstallApp(app, getApplicationContext()));
        threads.add(appThread);
  }

  private void displayRecommendedApps() {
      ArrayList<App> recommendedApps = new ArrayList<App>();
      for(App currentApp : apps) {
        if(!currentApp.isRecommendedApp) {
            continue;
        }
        recommendedApps.add(currentApp);
      }

      var lv = (ListView)findViewById(R.id.app_list);
 
      AppAdapter appAdapter = new AppAdapter(recommendedApps, this);
      lv.setAdapter(appAdapter);
  }

    private void buildAppList(String json_data) {
        System.out.println(json_data);
        try {
            JSONObject json = new JSONObject(json_data);
            var packages = json.getJSONObject("packages");
            for(int i = 0; i < packages.length(); i++) {
                App currentApp = new App();
                var name = packages.names().get(i);
                var packge = packages.getJSONObject(name.toString());
                var real_name = packge.getJSONObject("metadata").getJSONObject("name").get("en-US");
                currentApp.package_name = real_name.toString();

                var categories = packge.getJSONObject("metadata").get("categories");
                if(categories.toString().contains("Wizard"))
                {
                    currentApp.isWizardApp = true;
                }
                if(categories.toString().contains("Recommended"))
                {
                    currentApp.isRecommendedApp = true;
                }

                var versions = packge.getJSONObject("versions");
                var first_version = versions.names().get(0);
                var url = versions.getJSONObject(first_version.toString())
                        .getJSONObject("file").get("name").toString()
                        .replace(":Zone.Identifier", "").replace(" ", "%20");

                currentApp.fileName = url;

                url = "https://appstore.privacysociety.org/fdroid/repo/" + url;
                currentApp.url = url;
//                Log.v(TAG, url);

                if(currentApp.mimeCode.equals("Brave")) { 
                    currentApp.mimeCode = "application/xhtml+xml";
                }
                apps.add(currentApp);
            }

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public class DefaultApp implements Runnable { 
        App app = null;
        Activity activity = null;
        public DefaultApp(App app, Activity activity) {
            this.app = app;
            this.activity = activity;
        }
        public void run() {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .setDataAndType(null, app.mimeCode);
            activity.startActivity(Intent.createChooser(intent,"Select Default App"));
        }
    }

    public class WaitForInstallation implements Runnable {
        ArrayList<Thread> threads = null;
        Consumer<String> con = null;
        Consumer<String> block = null;
        Activity activity = null;
        public WaitForInstallation(ArrayList<Thread> threads, Consumer<String> con, Consumer<String> block, Activity activity) {
            this.threads = threads;
            this.con = con;
            this.activity = activity;
            this.block = block;
        }
        public void run() {
            for(Thread t : threads) {
                t.start();
            }
            for(int i = 0; i < threads.size(); i++) {
                Thread thread = threads.get(i);
                while(thread.isAlive()) {
                    //Do Nothing, just wait
                    block.accept("Block");
                }
                threads.remove(i);
            }
            Log.v(TAG, "Apps Installed");
            con.accept("Apps Installed");

//            TelecomManager telecomManager = (TelecomManager) activity.getApplicationContext().getSystemService(Context.TELECOM_SERVICE);
//            Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
//                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, "org.privacysociety.dialer");

//            Toast.makeText(activity, "Select the phone application.", Toast.LENGTH_LONG).show();
//
//            Intent intent = (RoleManager) activity.getApplicationContext()
//                                         .getSystemService(Context.ROLE_SERVICE)
//                                         .createRequestRoleIntent(RoleManager.ROLE_DIALER);
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            activity.startActivityForResult(intent, 1);

            DefaultDialerManager.setDefaultDialerApplication(activity, "org.privacysociety.dialer");
        }
    }

    public class SetWallpaper implements Runnable {
        Activity activity = null;
        public SetWallpaper(Activity activity) {
            this.activity = activity;
        }
        public void run() {
            try {
                InputStream input = new FileInputStream("/system/product/media/wallpaper.png");
                WallpaperManager wm = WallpaperManager.getInstance(activity);
                wm.setStream(input);
            } catch (Exception e) {
                e.printStackTrace();
            }
        
        }
    }

    @Override
    public void onNavigateNext() {
        Toast.makeText(this, "Installing Apps.", Toast.LENGTH_LONG).show();
        setNextAllowed(false);
        SystemBarHelper.setBackButtonVisible(getWindow(), false);

        var lv = (ListView)findViewById(R.id.app_list);
        List<App> chosenApps = new ArrayList<App>();

        for(int i = 0; i < lv.getCount(); i++ ) {
            App app = (App) lv.getItemAtPosition(i);
            if(app.state)
            {
//                Log.v(TAG, "Item '" + i + "' is checked");
                chosenApps.add(app);
            }
        }


        for(App currentApp : apps) {
           boolean skip = false;
           if(currentApp.isWizardApp)
           {
                for(App wizardApp : chosenApps) {
                    if(currentApp.package_name.equals(wizardApp.package_name)) {
                        skip = true;
                    }
                }
                if(skip) {
                    continue;
                }
                else {
                    chosenApps.add(currentApp);
                }
           }
           if(!currentApp.mimeCode.equals("")) {
                Thread t = new Thread(new DefaultApp(currentApp, this));
                threads.add(t);
                t.start();
           }
        }

        for(App currentApp : chosenApps) {
            installApp(currentApp);
        }

        Thread wp = new Thread(new SetWallpaper(this));
        threads.add(wp);

        Consumer<String> con = i -> super.onNavigateNext();
        Consumer<String> block = i -> setNextAllowed(false);
        
        Thread t = new Thread(new WaitForInstallation(threads, con, block, this));
        t.start();


    }

  private void applyMiscSetting() {
      SetupWizardApp mSetupWizardApp = (SetupWizardApp) getApplication();
      final Bundle myPageBundle = mSetupWizardApp.getSettingsBundle();
      myPageBundle.putBoolean(KEY_SEND_METRICS, false);

      ConnectivitySettingsManager.setPrivateDnsMode(getApplicationContext(), PRIVATE_DNS_MODE_PROVIDER_HOSTNAME);
      ConnectivitySettingsManager.setPrivateDnsHostname(getApplicationContext(),"dns.quad9.net");

      NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getApplicationContext());
      if(nfcAdapter != null) {
          nfcAdapter.disable();
      }

      updateLineageFeatureSetting(TrustInterface.TRUST_WARN_SELINUX);
      updateLineageFeatureSetting(TrustInterface.TRUST_WARN_PUBLIC_KEY);
  }

  private void updateLineageFeatureSetting(int feature) {
      int original = LineageSettings.Secure.getInt(getApplicationContext().getContentResolver(),
            LineageSettings.Secure.TRUST_WARNINGS, TrustInterface.TRUST_WARN_MAX_VALUE);
      int newValue = false ? (original | feature) : (original & ~feature);
      boolean success = LineageSettings.Secure.putInt(getApplicationContext().getContentResolver(),
            LineageSettings.Secure.TRUST_WARNINGS,
            newValue & TrustInterface.TRUST_WARN_MAX_VALUE);

      TrustInterface mInterface = TrustInterface.getInstance(getApplicationContext());
      mInterface.removeNotificationForFeature(feature);
  }

  @Override
  protected int getLayoutResId() {
      return R.layout.setup_fdroid_installer;
  }

  @Override
  protected int getTitleResId() {
      return R.string.setup_fdroid_title;
  }
}

