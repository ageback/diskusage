/**
 * DiskUsage - displays sdcard usage on android.
 * Copyright (C) 2008-2011 Ivan Volosyuk
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.google.android.diskusage;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.storage.StorageManager;
import android.system.Os;
import android.system.StructStat;
import android.system.StructStatVfs;
import android.util.Log;

import com.google.android.diskusage.entity.FileSystemEntry;
import com.google.android.diskusage.entity.FileSystemPackage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Apps2SDLoader {

  private final DiskUsage diskUsage;
  private CharSequence lastAppName = "";
  private boolean switchToSecondary = true;
  private int numLoadedPackages = 0;

  public Apps2SDLoader(DiskUsage activity) {
    this.diskUsage = activity;
  }


  public FileSystemEntry[] load(final int blockSize) throws Throwable {
    UsageStatsManager usageStatsManager = (UsageStatsManager) diskUsage.getSystemService(Context.USAGE_STATS_SERVICE);


    final List<UsageStats> queryUsageStats=usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_YEARLY, 0 ,System.currentTimeMillis());
    Log.d("diskusage", "stats size = " + queryUsageStats.size());
    StorageStatsManager storageStatsManager = (StorageStatsManager) diskUsage.getSystemService(Context.STORAGE_STATS_SERVICE);
    final ArrayList<FileSystemEntry> entries = new ArrayList<FileSystemEntry>();
    PackageManager packageManager = diskUsage.getApplicationContext().getPackageManager();
    final Set<String> packages = new HashSet<>();
    for (UsageStats s : queryUsageStats) {
      packages.add(s.getPackageName());
    }

    final Handler handler = diskUsage.handler;
    Runnable progressUpdater = new Runnable() {
      @Override
      public void run() {
        MyProgressDialog dialog = diskUsage.getPersistantState().loading;
        if (dialog != null) {
          if (switchToSecondary) {
            dialog.switchToSecondary();
            switchToSecondary = false;
          }
          dialog.setMax(packages.size());
          final CharSequence appName;
          synchronized (Apps2SDLoader.this) {
            appName = lastAppName;
          }
          dialog.setProgress(numLoadedPackages, appName);
        }
        diskUsage.handler.postDelayed(this, 50);
      }
    };
    handler.post(progressUpdater);


    for (String pkg : packages) {
      Log.d("diskusage", "app: " + pkg);
      try {
        ApplicationInfo metadata = packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA);
        String appName = metadata.loadLabel(packageManager).toString();
        lastAppName = appName;
        StorageStats stats = storageStatsManager.queryStatsForPackage(
                StorageManager.UUID_DEFAULT, pkg, android.os.Process.myUserHandle());
        Log.d("diskusage", "stats: " + stats.getAppBytes() + " " + stats.getDataBytes());
        FileSystemPackage p = new FileSystemPackage(
                appName,
                pkg,
                stats.getAppBytes(),
                stats.getDataBytes(),
                stats.getCacheBytes(),
                metadata.flags);
        p.applyFilter(blockSize);
        entries.add(p);
        numLoadedPackages++;
      } catch (PackageManager.NameNotFoundException e) {
        Log.d("diskusage", "Failed to get package", e);
      }
    }

    FileSystemEntry[] result = entries.toArray(new FileSystemEntry[] {});
    Arrays.sort(result, FileSystemEntry.COMPARE);
    handler.removeCallbacks(progressUpdater);
    StructStatVfs a = Os.statvfs("/sdcard");
    StructStat s = Os.stat("/sdcard");
    return result;
  }
}
