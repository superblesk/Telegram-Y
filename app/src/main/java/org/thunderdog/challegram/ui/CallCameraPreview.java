/*
 * This file is a part of Telegram Y
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import org.thunderdog.challegram.Log;

/**
 * Local camera preview shown before the call engine is ready and starts producing frames.
 */
public final class CallCameraPreview {
  private final Context context;
  private final PreviewView view;
  private ProcessCameraProvider provider;
  private int generation;

  public CallCameraPreview (@NonNull Context context) {
    this.context = context;
    this.view = new PreviewView(context);
    this.view.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
    this.view.setScaleType(PreviewView.ScaleType.FILL_CENTER);
    this.view.setClickable(false);
  }

  @NonNull
  public View getView () {
    return view;
  }

  public void start (boolean frontFacing) {
    final int currentGeneration = ++generation;
    final com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
    future.addListener(() -> {
      if (currentGeneration != generation) {
        return;
      }
      try {
        provider = future.get();
      } catch (Throwable t) {
        Log.e(Log.TAG_VOIP, "Unable to open camera preview", t);
        return;
      }
      if (currentGeneration != generation) {
        return;
      }
      try {
        provider.unbindAll();
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(view.getSurfaceProvider());
        provider.bindToLifecycle((LifecycleOwner) context, new CameraSelector.Builder()
          .requireLensFacing(frontFacing ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
          .build(), preview);
        view.setScaleX(frontFacing ? -1f : 1f);
      } catch (Throwable t) {
        Log.e(Log.TAG_VOIP, "Unable to start camera preview", t);
      }
    }, ContextCompat.getMainExecutor(context));
  }

  public void stop () {
    generation++;
    if (provider != null) {
      try {
        provider.unbindAll();
      } catch (Throwable ignored) { }
      provider = null;
    }
  }
}
