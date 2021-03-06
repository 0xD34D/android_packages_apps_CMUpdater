/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at http://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.cyanogenmod.updater.receiver;

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;
import android.widget.Toast;

import com.cyanogenmod.updater.R;
import com.cyanogenmod.updater.UpdatePreference;
import com.cyanogenmod.updater.UpdatesSettings;
import com.cyanogenmod.updater.misc.Constants;
import com.cyanogenmod.updater.utils.MD5;

import java.io.File;

public class DownloadCompletedReceiver extends BroadcastReceiver{
    private static String TAG = "DownloadCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        // Get the ID of the currently running CMUpdater download and the one just finished
        SharedPreferences prefs = context.getSharedPreferences("CMUpdate", Context.MODE_MULTI_PROCESS);
        long enqueue = prefs.getLong(Constants.DOWNLOAD_ID, -1);
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -2);

        // If we had an active download and the id's match
        if (enqueue != -1 && id != -2 && id == enqueue) {
            Query query = new Query();
            query.setFilterById(id);
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            Cursor c = dm.query(query);
            if (c.moveToFirst()) {
                int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = c.getInt(columnIndex);
                if (status == DownloadManager.STATUS_SUCCESSFUL) {

                    // Get the full path name of the downloaded file and the MD5
                    int filenameIndex = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME);
                    String fullPathName = c.getString(filenameIndex);
                    File destinationFile = new File(fullPathName);
                    String downloadedMD5 = prefs.getString(Constants.DOWNLOAD_MD5, "");

                    // Clear the shared prefs
                    prefs.edit().putString(Constants.DOWNLOAD_MD5, "").apply();
                    prefs.edit().putLong(Constants.DOWNLOAD_ID, -1).apply();
                    prefs.edit().putString(Constants.DOWNLOAD_URL, "").apply();

                    // Start the MD5 check of the downloaded file
                    if (MD5.checkMD5(downloadedMD5, destinationFile)) {
                        // We passed. Bring the main app to the foreground and trigger download completed
                        Intent i = new Intent(context, UpdatesSettings.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP |
                                Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        i.putExtra(Constants.DOWNLOAD_COMPLETED, true);
                        i.putExtra(Constants.DOWNLOAD_ID, id);
                        i.putExtra(Constants.DOWNLOAD_FULLPATH, fullPathName);
                        context.startActivity(i);

                    } else {
                        // We failed. Clear the file and reset everything
                        dm.remove(id);
                        Toast.makeText(context, R.string.md5_verification_failed, Toast.LENGTH_LONG).show();
                    }

                } else if (status == DownloadManager.STATUS_FAILED) {
                    // The download failed, reset
                    dm.remove(id);
                    prefs.edit().putLong(Constants.DOWNLOAD_ID, -1).apply();
                    prefs.edit().putString(Constants.DOWNLOAD_MD5, "").apply();
                    prefs.edit().putString(Constants.DOWNLOAD_URL, "").apply();
                    Toast.makeText(context, R.string.unable_to_download_file, Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
