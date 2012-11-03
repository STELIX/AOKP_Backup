/*
 * Copyright (C) 2012 Roman Birg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aokp.backup.restore;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import com.aokp.backup.categories.ICSCategories;
import com.aokp.backup.util.SVal;
import com.aokp.backup.util.ShellCommand;
import com.aokp.backup.util.Tools;

import java.io.*;
import java.util.HashMap;

public abstract class Restore {

    public static final String TAG = "Restore";

    Context mContext;

    HashMap<String, SVal> settingsFromFile;
    String name;

    public static final int ERROR_IOEXCEPTION = 1;
    public static final int ERROR_RESTORE_UNSUPPORTED = 2;
    public static final int ERROR_NOT_AOKP = 3;
    public static final int ERROR_XOS = 4;

    String rcUser = null;
    File rcFilesDir;
    File rcPrefsDir;

    int restoreResult = 0;

    public Restore(Context c) {
        mContext = c;
    }

    public int restoreSettings(final String name, final boolean[] catsToRestore) {
        this.name = name;

        if (!readRestore()) {
            Log.e(TAG, "error reading restore cfg!");
            return ERROR_IOEXCEPTION;
        }

        rcUser = getRomControlPid();
        if (rcUser == null) {
            return ERROR_IOEXCEPTION;
        }

        Log.e(TAG, "folders go");
        rcFilesDir = new File("/data/data/com.aokp.romcontrol/files/");
        if (!rcFilesDir.exists()) {
            new ShellCommand().su.runWaitFor("mkdir " + rcFilesDir.getAbsolutePath());
            Tools.chmodAndOwn(rcFilesDir, "0660", rcUser);
        }
        Log.e(TAG, "setup files");
        rcPrefsDir = new File("/data/data/com.aokp.romcontrol/shared_prefs/");
        if (!rcPrefsDir.exists()) {
            new ShellCommand().su.runWaitFor("mkdir " + rcPrefsDir.getAbsolutePath());
            Tools.chmodAndOwn(rcPrefsDir, "0660", rcUser);
        }

        Log.e(TAG, "setup folders");

        for (int i = 0; i < catsToRestore.length; i++) {
            if (catsToRestore[i]) {
                restoreSettings(i);
            }
        }
        return 0;
    }

    public static String getRomControlPid() {
        Process p = new ShellCommand().su
                .run("ls -ld /data/data/com.aokp.romcontrol/ | awk '{print $3}' | less");
        Log.i(TAG, "process has run");
        try {
            InputStreamReader ir = new InputStreamReader(p.getInputStream());
            char[] array = new char[10];
            int read = ir.read(array);
            ir.close();
            if (read == -1)
                read = 10;
            StringBuffer uid = new StringBuffer();
            for (int i = 0; i < read; i++) {
                uid.append(array[i]);
            }
            return uid.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // ActivityManager am = (ActivityManager)
        // mContext.getSystemService(Context.ACTIVITY_SERVICE);
        // List<RunningAppProcessInfo> service = am.getRunningAppProcesses();
        // for (RunningAppProcessInfo app : service) {
        // for (String pkg : app.pkgList) {
        // if (pkg.equals("com.aokp.romcontrol")) {
        // Log.i(TAG, "ROMControl uid: " + app.uid);
        // return "" + app.uid;
        // }
        // }
        // }
        return null;
    }

    public abstract boolean okayToRestore();

    protected int getAOKPBackupVersionInteger() {
        try {
            String contents = Tools.readFileToString(new File(Tools.getBackupDirectory(mContext,
                    name), "aokp.version"));
            return Integer.parseInt(contents);
        } catch (Exception e) {
            return -1;
        }
    }

    protected boolean readRestore() {
        File f = new File(Tools.getBackupDirectory(mContext, name), "settings.cfg");
        if (!f.exists()) {
            Log.e(TAG, "settings.cfg doesn't exist!");
        }
        settingsFromFile = new HashMap<String, SVal>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(f));
            String strLine = null;
            while ((strLine = br.readLine()) != null) {
                // System.out.println(strLine);
                int split = strLine.indexOf("=");
                String setting = strLine.substring(0, split);
                String value = strLine.substring(split + 1, strLine.length());
                settingsFromFile.put(setting, new SVal(setting, value));
            }
            br.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "error", e);
            return false;
        }
    }

    private void restoreSettings(int category) {
        String[] settingsArray = new ICSCategories().getSettingsCategory(mContext, category);

        if (settingsArray == null) {
            Log.w(TAG, "couldn't find array of settings for category: "
                    + category);
            return;
        }

        for (String s : settingsArray) {
            SVal settingToRestore = settingsFromFile.get(s);
            if (!shouldHandleSpecialCase(s) && settingsFromFile.containsKey(s)) {
                restoreSetting(settingToRestore);
            }
        }
    }

    /**
     * Every setting should be checked through this function. It will check the
     * setting and perform any special actions for this specific setting.
     * 
     * @param setting setting name that needs will be checked
     * @return whether a special case was handled
     */
    public abstract boolean shouldHandleSpecialCase(String setting);

    protected boolean restoreSetting(String setting, String value, boolean secure) {
        // Log.d(TAG, "restoring: " + setting + " val: "
        // + value);
        // return true;
        if (secure)
            return Settings.Secure.putString(mContext.getContentResolver(),
                    setting, value);
        else
            return Settings.System.putString(mContext.getContentResolver(),
                    setting, value);

    }

    public boolean restoreSetting(SVal setting) {
        return restoreSetting(setting.getRealSettingString(), setting.getVal(), setting.isSecure());
    }
}
