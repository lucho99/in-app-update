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

import java.security.MessageDigest;
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
    public static final String FILE_NAME = "www";
    public static final String FILE_EXTENSION = "zip";
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
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Log.v(LOG_TAG, "execute: action=" + action);
        gWebView = this.webView;

        if (action.equals(ACTION_DOWNLOAD)) {
            this.downloadUpdate(args.getString(0), args.getString(1), callbackContext);
            return true;
        }
        if (action.equals(ACTION_INSTALL)) {
            this.installUpdate(callbackContext);
            return true;
        }
        if (action.equals(ACTION_APPLY_UPDATE)) {
            this.applyUpdate();
            return true;
        }

        Log.e(LOG_TAG, "Invalid action : " + action);
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
        return false;
    }

    private void downloadUpdate(final String fileURL, final String fileChecksum, final CallbackContext callbackContext) {
        Log.v(LOG_TAG, "Download URL: " + fileURL);
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                boolean _hasWifiConnection = hasWifiConnection();
                String _fileName = FILE_NAME + "." + FILE_EXTENSION;

                if (_hasWifiConnection) {
                    try {
                        URL url = new URL(fileURL);
                        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
                        int responseCode = httpConn.getResponseCode();

                        // always check HTTP response code first
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            // opens input stream from the HTTP connection
                            InputStream inputStream = httpConn.getInputStream();

                            Context context = getApplicationContext();
                            String dataDir = context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
                                    .applicationInfo.dataDir;
                            String saveFilePath = dataDir + File.separator + _fileName;

                            // opens an output stream to save into file
                            FileOutputStream outputStream = new FileOutputStream(saveFilePath);

                            int bytesRead = -1;
                            byte[] buffer = new byte[BUFFER_SIZE];
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }

                            outputStream.close();
                            inputStream.close();

                            String _checksum = checksum(saveFilePath);
                            if (_checksum.toUpperCase().equals(fileChecksum.toUpperCase())) {
                                callbackContext.success("{\"downloaded\": true}");
                                Log.v(LOG_TAG, "Downloaded file: " + _fileName);
                            } else {
                                callbackContext.success("{\"downloaded\": false, \"message\":\"Not a valid file.\"}");
                            }
                        } else {
                            callbackContext.error("{\"downloaded\": false}");
                        }

                        httpConn.disconnect();
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "Error: " + e.getMessage());
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }
                } else {
                    callbackContext.error("{\"downloaded\": false, \"message\":\"No Wifi connection available.\"}");
                }
            }
        });
    }

    private void installUpdate(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                Context context = getApplicationContext();

                try {
                    String _fileName = FILE_NAME + "." + FILE_EXTENSION;
                    String path = context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
                            .applicationInfo.dataDir;

                    File dir = new File(path + File.separator + "www");
                    if (dir.isDirectory()) {
                        deleteFolder(path + File.separator + "www");
                    }

                    boolean isUnpacked = unpackZip(path + "/" + _fileName, path);

                    if (isUnpacked) {
                        File file = new File(path, _fileName);
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
        });
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

    private boolean hasWifiConnection() {
        ConnectivityManager connManager = (ConnectivityManager) getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifi.isConnected();
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

    public static String checksum(String filePath) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(filePath);
            byte[] buffer = new byte[1024];
            MessageDigest digest = MessageDigest.getInstance("MD5");
            int numRead = 0;
            while (numRead != -1) {
                numRead = inputStream.read(buffer);
                if (numRead > 0)
                    digest.update(buffer, 0, numRead);
            }
            byte [] md5Bytes = digest.digest();
            return convertHashToString(md5Bytes);
        } catch (Exception e) {
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) { }
            }
        }
    }

    private static String convertHashToString(byte[] md5Bytes) {
        String returnVal = "";
        for (int i = 0; i < md5Bytes.length; i++) {
            returnVal += Integer.toString(( md5Bytes[i] & 0xff ) + 0x100, 16).substring(1);
        }
        return returnVal.toUpperCase();
    }
}