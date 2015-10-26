package com.dixtra.cordova.inAppUpdate;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.regex.*;

import java.net.URL;
import java.net.HttpURLConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;

import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

public class InAppUpdatePlugin extends CordovaPlugin {

    public static final String LOG_TAG = "InAppUpdatePlugin";
    public static final String ACTION_CHECK = "check";
    public static final String ACTION_DOWNLOAD = "download";
    public static final String ACTION_INSTALL = "install";
    public static final String ACTION_APPLY_UPDATE = "applyUpdate";
    public static final int BUFFER_SIZE = 4096;

    private static CordovaWebView gWebView;

    /**
     * Gets the application context from cordova's main activity.
     * @return the application context
     */
    private Context getApplicationContext() {
        return this.cordova.getActivity().getApplicationContext();
    }

    @Override
    public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {
        Log.v(LOG_TAG, "execute: action=" + action);
        gWebView = this.webView;

        if (ACTION_CHECK.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    checkForUpdate(callbackContext);
                }
            });
        } else if (ACTION_DOWNLOAD.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    downloadUpdate(callbackContext);
                }
            });
        } else if (ACTION_INSTALL.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    installUpdate(callbackContext);
                }
            });
        } else if (ACTION_APPLY_UPDATE.equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    applyUpdate();
                }
            });
        } else {
            Log.e(LOG_TAG, "Invalid action : " + action);
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }

        return true;
    }

    private void checkForUpdate(final CallbackContext callbackContext) {
        boolean _hasUpdates = hasUpdates();
        boolean _hasWifiConnection = hasWifiConnection();
        String jsonResult;

        if (_hasUpdates && _hasWifiConnection) {
            jsonResult = "{\"hasUpdates\": true}";
        } else {
            jsonResult = "{\"hasUpdates\": false}";
        }

        callbackContext.success(jsonResult);
    }

    private void downloadUpdate(final CallbackContext callbackContext) {
        String downloadUrl = getResString("download_url");
        String downloadFilename = getResString("download_filename");
        String _URL = downloadUrl + "/" + downloadFilename;

        Log.v(LOG_TAG, "Download URL: " + _URL);

        try {
            URL url = new URL(_URL);
            HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
            int responseCode = httpConn.getResponseCode();

            // always check HTTP response code first
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // opens input stream from the HTTP connection
                InputStream inputStream = httpConn.getInputStream();

                Context context = getApplicationContext();
                String dataDir = context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
                        .applicationInfo.dataDir;
                String saveFilePath = dataDir + File.separator + downloadFilename;

                // opens an output stream to save into file
                FileOutputStream outputStream = new FileOutputStream(saveFilePath);

                int bytesRead = -1;
                byte[] buffer = new byte[BUFFER_SIZE];
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();

                callbackContext.success("{\"downloaded\": true}");
                Log.v(LOG_TAG, "Downloaded file: " + downloadFilename);
            } else {
                callbackContext.error("{\"downloaded\": false}");
            }

            httpConn.disconnect();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error: " + e.getMessage());
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void installUpdate(final CallbackContext callbackContext) {
        Context context = getApplicationContext();

        try {
            String downloadFilename = getResString("download_filename");
            String path = context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
                    .applicationInfo.dataDir;

            File dir = new File(path + File.separator + "www");
            if (dir.isDirectory()) {
                deleteFolder(path + File.separator + "www");
            }

            boolean isUnpacked = unpackZip(path + "/" + downloadFilename, path);

            if (isUnpacked) {
                File file = new File(path, downloadFilename);
                boolean deleted = file.delete();

                if (deleted) {
                    callbackContext.success("{\"installed\": true}");
                } else {
                    callbackContext.error("{\"installed\": false}");
                }
            } else {
                callbackContext.error("{\"installed\": false}");
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void deleteFolder(String s) {
        File dir = new File(s);
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                File childrenDir = new File(dir, children[i]);
                if (childrenDir.isDirectory()) {
                    deleteFolder(childrenDir.getAbsolutePath());
                } else {
                    childrenDir.delete();
                }
            }
        }
    }

    private void applyUpdate() {
        Context context = getApplicationContext();
        try {
            String path = context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
                    .applicationInfo.dataDir;
            gWebView.loadUrl("file:///" + path + "/www/index.html");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private boolean hasUpdates() {
        String checkUrl = getResString("check_url");
        String checkAttr = getResString("check_attr");

        try {
            URL url = new URL(checkUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            try {
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                String result = getStringFromInputStream(in);

                try {
                    JSONObject json = new JSONObject(result);

                    Pattern patternNumber = Pattern.compile("0|1");
                    Pattern patternBoolean = Pattern.compile("true|false");
                    
                    Matcher matcherNumber = patternNumber.matcher(json.getString(checkAttr));
                    Matcher matcherBoolean = patternBoolean.matcher(json.getString(checkAttr));

                    if (matcherNumber.matches()) {
                        return Integer.parseInt(json.getString(checkAttr)) == 1 ? true : false;
                    }
                    if (matcherBoolean.matches()) {
                        return json.getString(checkAttr) == "true" ? true : false;
                    }
                } catch (JSONException e) {
                    Log.e(LOG_TAG, "Error parsing data " + e.toString());
                }
            } catch (IOException e) {
            } finally {
                urlConnection.disconnect();
            }
        } catch (IOException e) {
            return false;
        }

        return false;
    }

    private boolean hasWifiConnection() {
        ConnectivityManager connManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifi.isConnected();
    }

    private String getResString(String identifier) {
        int resId = cordova.getActivity().getResources().getIdentifier(identifier, "string",
                cordova.getActivity().getPackageName());
        return cordova.getActivity().getString(resId);
    }

    // convert InputStream to String
    private String getStringFromInputStream(InputStream is) {
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();

        String line;
        try {

            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return sb.toString();
    }

    private boolean unpackZip(String srcPath, String destPath) {
        InputStream inputstream;
        ZipInputStream zipinputstream;

        try {
            String filename;
            inputstream = new FileInputStream(srcPath);
            zipinputstream = new ZipInputStream(new BufferedInputStream(inputstream));
            ZipEntry mZipEntry;
            byte[] buffer = new byte[1024];
            int count;

            while ((mZipEntry = zipinputstream.getNextEntry()) != null) {
                filename = mZipEntry.getName();

                if (mZipEntry.isDirectory()) {
                    File fmd = new File(destPath + File.separator + filename);
                    fmd.mkdirs();
                    continue;
                }

                FileOutputStream fileoutputstream = new FileOutputStream(destPath + File.separator + filename);

                while ((count = zipinputstream.read(buffer)) != -1) {
                    fileoutputstream.write(buffer, 0, count);
                }

                fileoutputstream.close();
                zipinputstream.closeEntry();
            }

            zipinputstream.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}