package org.pytgcalls.ntgcallsx;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.text.TextUtils;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import org.drinkless.tdlib.TdApi;
import io.github.pytgcalls.ConnectionInfo;
import io.github.pytgcalls.ConnectionKind;
import io.github.pytgcalls.ConnectionState;
import io.github.pytgcalls.FramesCallback;
import io.github.pytgcalls.NTgCalls;
import io.github.pytgcalls.RemoteSourceChangeCallback;
import io.github.pytgcalls.exceptions.ConnectionException;
import io.github.pytgcalls.exceptions.ConnectionNotFoundException;
import io.github.pytgcalls.media.AudioDescription;
import io.github.pytgcalls.media.DeviceInfo;
import io.github.pytgcalls.media.Format;
import io.github.pytgcalls.media.MediaDescription;
import io.github.pytgcalls.media.MediaDevices;
import io.github.pytgcalls.media.MediaSource;
import io.github.pytgcalls.media.StreamMode;
import io.github.pytgcalls.media.VideoDescription;
import io.github.pytgcalls.p2p.RTCServer;

import io.github.pytgcalls.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.voip.ConnectionStateListener;
import org.thunderdog.challegram.voip.NetworkStats;
import org.thunderdog.challegram.voip.VoIPInstance;
import org.thunderdog.challegram.voip.annotation.CallState;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NTgCallsInterface implements CallInterface {
  private static final long CALL_ID = 0;
  private final NTgCalls ntgcalls;
  private final @Nullable AudioDescription micDescription;
  private static final int CAPTURE_WIDTH = 1920;
  private static final int CAPTURE_HEIGHT = 1080;
  private static final int AUTO_DETECT = -1;
  private final Tdlib tdlib;
  private final TdApi.Call call;
  private volatile boolean destroying;

  private static final FramesCallback NO_OP_FRAMES = (callId, mode, device, frames) -> { };
  private static final RemoteSourceChangeCallback NO_OP_REMOTE_SOURCE = (callId, source) -> { };

  public NTgCallsInterface (Tdlib tdlib, TdApi.Call call, TdApi.CallStateReady state, ConnectionStateListener listener, boolean forceTcp) throws ConnectionException, FileNotFoundException {
    this.tdlib = tdlib;
    this.call = call;
    if (ContextCompat.checkSelfPermission(AppContext.get(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      throw new SecurityException("No microphone permission");
    }
    MediaDevices devices = NTgCalls.getMediaDevices();
    if (devices.microphone.isEmpty() || devices.speaker.isEmpty()) {
      throw new IllegalStateException("No audio devices available");
    }
    ntgcalls = new NTgCalls();
    ntgcalls.onSignalingData((callId, data) -> {
      if (!destroying) {
        listener.onSignallingDataEmitted(data);
      }
    });
    ntgcalls.onConnectionChange((chatId, callNetworkState) -> {
      if (callNetworkState.kind != ConnectionKind.NORMAL) {
        return; // presentation connection state must not finish the main call
      }
      if (destroying) {
        return; // local hangup already in progress
      }
      if (callNetworkState.state == ConnectionState.CONNECTED) {
        listener.onConnectionStateChanged(null, CallState.ESTABLISHED);
      } else if (callNetworkState.state == ConnectionState.FAILED ||
        callNetworkState.state == ConnectionState.TIMEOUT ||
        callNetworkState.state == ConnectionState.CLOSED) {
        // CLOSED also arrives for a remote disconnect, after which NTgCalls removes the call.
        listener.onConnectionStateChanged(null, CallState.FAILED);
      }
      // CONNECTING is a normal transition.
    });
    micDescription = new AudioDescription(
      MediaSource.DEVICE,
      48000,
      1,
      devices.microphone.get(0).metadata,
      true
    );
    ntgcalls.createP2pCall(CALL_ID);
    ntgcalls.setStreamSources(
      CALL_ID,
      StreamMode.CAPTURE,
      new MediaDescription(
        micDescription,
        null,
        null,
        null
      )
    );
    ntgcalls.setStreamSources(
      CALL_ID,
      StreamMode.PLAYBACK,
      new MediaDescription(
        new AudioDescription(
          MediaSource.DEVICE,
          48000,
          2,
          devices.speaker.get(0).metadata,
          true
        ),
        null,
        new VideoDescription(
          MediaSource.EXTERNAL,
          AUTO_DETECT,
          AUTO_DETECT,
          30,
          "",
          true,
          Format.I420
        ),
        null
      )
    );
    ntgcalls.skipExchange(CALL_ID, state.encryptionKey, call.isOutgoing);
    var rtcServers = Arrays.stream(state.servers)
      .map(server -> {
        if (server.type instanceof TdApi.CallServerTypeWebrtc webrtc) {
          return new RTCServer(
            server.id,
            server.ipAddress,
            server.ipv6Address,
            server.port,
            webrtc.username,
            webrtc.password,
            webrtc.supportsTurn,
            webrtc.supportsStun,
            forceTcp,
            null
          );
        } else {
          var reflector = (TdApi.CallServerTypeTelegramReflector) server.type;
          return new RTCServer(
            server.id,
            server.ipAddress,
            server.ipv6Address,
            server.port,
            null,
            null,
            true,
            false,
            forceTcp || reflector.isTcp,
            reflector.peerTag
          );
        }
      }).collect(Collectors.toList());
    ntgcalls.connectP2p(
      CALL_ID,
      rtcServers,
      List.of(state.protocol.libraryVersions),
      state.allowP2p,
      TextUtils.isEmpty(state.customParameters) ? null : state.customParameters
    );
  }

  @Override
  public boolean isVideoSupported () {
    return true;
  }

  @Override
  public void setFrameCallback (FramesCallback callback) {
    ntgcalls.onFrames(callback);
  }

  @Override
  public void setRemoteSourceChangeCallback (RemoteSourceChangeCallback callback) {
    ntgcalls.onRemoteSourceChange(callback);
  }

  @Override
  public long getCallDuration () {
    try {
      return ntgcalls.time(CALL_ID, StreamMode.PLAYBACK) * 1000;
    } catch (ConnectionNotFoundException e) {
      return VoIPInstance.DURATION_UNKNOWN;
    }
  }

  @Override
  public void setAudioOutputGainControlEnabled (boolean enabled) {
    // NTgCalls has no public API for output gain control; WebRTC handles it internally.
  }

  @Override
  public void handleIncomingSignalingData (byte[] data) {
    try {
      ntgcalls.sendSignalingData(CALL_ID, data);
    } catch (ConnectionException e) {
      Log.e(Log.TAG_VOIP, "Error sending signaling data", e);
    }
  }

  @Override
  public void setEchoCancellationStrength (int strength) {
    // NTgCalls has no public API for the echo cancellation strength.
  }

  @Override
  public void setMicDisabled (boolean disabled) {
    try {
      if (disabled) {
        ntgcalls.mute(CALL_ID);
      } else {
        ntgcalls.unmute(CALL_ID);
      }
    } catch (ConnectionNotFoundException e) {
      Log.e(Log.TAG_VOIP, "Error setting call settings", e);
    }
  }

  @Override
  public void setCameraEnabled (boolean enabled, boolean front) {
    try {
      if (enabled) {
        if (ContextCompat.checkSelfPermission(AppContext.get(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
          Log.w(Log.TAG_VOIP, "No camera permission, ignoring camera request");
          return;
        }
        var cameras = NTgCalls.getMediaDevices().camera;
        String cameraId = findCameraId(cameras, front);
        if (cameraId == null) {
          Log.w(Log.TAG_VOIP, "No camera available for the call");
          return;
        }
        ntgcalls.setStreamSources(
          CALL_ID,
          StreamMode.CAPTURE,
          new MediaDescription(
            micDescription,
            null,
            new VideoDescription(
              MediaSource.DEVICE,
              CAPTURE_WIDTH,
              CAPTURE_HEIGHT,
              30,
              cameraId,
              true
            ),
            null
          )
        );
      } else {
        ntgcalls.setStreamSources(
          CALL_ID,
          StreamMode.CAPTURE,
          new MediaDescription(
            micDescription,
            null,
            null,
            null
          )
        );
      }
    } catch (ConnectionNotFoundException e) {
      Log.e(Log.TAG_VOIP, "Error setting camera", e);
    }
  }

  @Override
  public void setScreenShareEnabled (boolean enabled) {
    var size = getScreenCaptureSize();
    try {
      if (enabled) {
        var screens = NTgCalls.getMediaDevices().screen;
        if (screens.isEmpty()) {
          Log.w(Log.TAG_VOIP, "No screen capture source available");
          return;
        }
        ntgcalls.setStreamSources(
          CALL_ID,
          StreamMode.CAPTURE,
          new MediaDescription(
            micDescription,
            null,
            null,
            new VideoDescription(
              MediaSource.DESKTOP,
              size.x,
              size.y,
              30,
              screens.get(0).metadata,
              true
            )
          )
        );
      } else {
        ntgcalls.setStreamSources(
          CALL_ID,
          StreamMode.CAPTURE,
          new MediaDescription(
            micDescription,
            null,
            null,
            null
          )
        );
      }
    } catch (ConnectionNotFoundException e) {
      Log.e(Log.TAG_VOIP, "Error setting screen share", e);
    }
  }

  @Override
  public long getConnectionId () {
    return 0;
  }

  @Override
  public void setNetworkType (int type) {
    // NTgCalls manages network candidates internally.
  }

  @Override
  public void getNetworkStats (NetworkStats stats) {
    // NTgCalls doesn't expose per-interface byte counters.
  }

  @Override
  public void clearVideoCallbacks () {
    setFrameCallback(NO_OP_FRAMES);
    setRemoteSourceChangeCallback(NO_OP_REMOTE_SOURCE);
  }

  @Override
  public void performDestroy () {
    destroying = true;
    clearVideoCallbacks();
    try {
      ntgcalls.stop(CALL_ID);
    } catch (ConnectionNotFoundException e) {
      Log.e(Log.TAG_VOIP, "Error stopping call", e);
    }
  }

  @Override
  public CharSequence collectDebugLog () {
    return "NTgCalls " + BuildConfig.VERSION_NAME + ", cpu usage: " + (int) (ntgcalls.cpuUsage() * 100) + "%";
  }

  @Override
  public TdApi.Call getCall () {
    return call;
  }

  @Override
  public Tdlib tdlib () {
    return tdlib;
  }

  @Override
  public boolean isInitiated () {
    return ntgcalls.calls().containsKey(CALL_ID);
  }

  @Override
  public String getLibraryName () {
    return "ntgcalls";
  }

  @Override
  public String getLibraryVersion () {
    return BuildConfig.VERSION_NAME;
  }

  private static @Nullable String findCameraId (List<DeviceInfo> cameras, boolean front) {
    if (cameras.isEmpty()) {
      return null;
    }
    String fallback = cameras.get(0).metadata;
    for (DeviceInfo camera : cameras) {
      try {
        if (new JSONObject(camera.metadata).optBoolean("is_front", !front) == front) {
          return camera.metadata;
        }
      } catch (JSONException ignored) { }
    }
    return fallback;
  }

  private static Point getScreenCaptureSize() {
    WindowManager wm = (WindowManager) AppContext.get().getSystemService(Context.WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();
    Point size = new Point();
    display.getRealSize(size);
    float aspect;
    if (size.x > size.y) {
      aspect = size.y / (float) size.x;
    } else {
      aspect = size.x / (float) size.y;
    }
    int dx = -1;
    int dy = -1;
    for (int a = 1; a <= 100; a++) {
      float val = a * aspect;
      if (val == (int) val) {
        if (size.x > size.y) {
          dx = a;
          dy = (int) (a * aspect);
        } else {
          dy = a;
          dx = (int) (a * aspect);
        }
        break;
      }
    }
    if (dx != -1 && aspect != 1) {
      while (size.x > 1000 || size.y > 1000 || size.x % 4 != 0 || size.y % 4 != 0) {
        size.x -= dx;
        size.y -= dy;
        if (size.x < 800 && size.y < 800) {
          dx = -1;
          break;
        }
      }
    }
    if (dx == -1 || aspect == 1) {
      float scale = Math.max(size.x / 970.0f, size.y / 970.0f);
      size.x = (int) Math.ceil((size.x / scale) / 4.0f) * 4;
      size.y = (int) Math.ceil((size.y / scale) / 4.0f) * 4;
    }
    return size;
  }
}
