package org.apache.cordova.firebase;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.net.Uri;
//import androidx.annotation.NonNull;
//import androidx.core.app.NotificationManagerCompat;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationManagerCompat;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.perf.metrics.Trace;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Firebase PhoneAuth
import java.util.concurrent.TimeUnit;

import me.leolin.shortcutbadger.ShortcutBadger;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

public class FirebasePlugin extends CordovaPlugin {

  private FirebaseAnalytics mFirebaseAnalytics;
  private static final String TAG = "FirebasePlugin";
  protected static final String KEY = "badge";

  private static boolean inBackground = true;
  private static ArrayList<Bundle> notificationStack = null;
  private static CallbackContext notificationCallbackContext;
  private static CallbackContext tokenRefreshCallbackContext;
  private static CallbackContext dynamicLinkCallback;

  @Override
  protected void pluginInitialize() {
    final Context context = this.cordova.getActivity().getApplicationContext();
    final Bundle extras = this.cordova.getActivity().getIntent().getExtras();
    this.cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        Log.d(TAG, "Starting Firebase plugin");
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
        mFirebaseAnalytics.setAnalyticsCollectionEnabled(true);
        if (extras != null && extras.size() > 1) {
          if (FirebasePlugin.notificationStack == null) {
            FirebasePlugin.notificationStack = new ArrayList<Bundle>();
          }
          if (extras.containsKey("google.message_id")) {
            extras.putBoolean("tap", true);
            notificationStack.add(extras);
          }
        }
      }
    });
  }

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    if (action.equals("getId")) {
      this.getId(callbackContext);
      return true;      
    } else if (action.equals("getToken")) {
      this.getToken(callbackContext);
      return true;
    } else if (action.equals("hasPermission")) {
      this.hasPermission(callbackContext);
      return true;
    } else if (action.equals("setBadgeNumber")) {
      this.setBadgeNumber(callbackContext, args.getInt(0));
      return true;
    } else if (action.equals("getBadgeNumber")) {
      this.getBadgeNumber(callbackContext);
      return true;
    } else if (action.equals("subscribe")) {
      this.subscribe(callbackContext, args.getString(0));
      return true;
    } else if (action.equals("unsubscribe")) {
      this.unsubscribe(callbackContext, args.getString(0));
      return true;
    } else if (action.equals("unregister")) {
      this.unregister(callbackContext);
      return true;
    } else if (action.equals("onNotificationOpen")) {
      this.onNotificationOpen(callbackContext);
      return true;
    } else if (action.equals("onTokenRefresh")) {
      this.onTokenRefresh(callbackContext);
      return true;
    } else if (action.equals("logEvent")) {
      this.logEvent(callbackContext, args.getString(0), args.getJSONObject(1));
      return true;
    } else if (action.equals("setScreenName")) {
      this.setScreenName(callbackContext, args.getString(0));
      return true;
    } else if (action.equals("setUserId")) {
      this.setUserId(callbackContext, args.getString(0));
      return true;
    } else if (action.equals("setUserProperty")) {
      this.setUserProperty(callbackContext, args.getString(0), args.getString(1));
      return true;
    } else if (action.equals("clearAllNotifications")) {
      this.clearAllNotifications(callbackContext);
      return true;
  }

    return false;
  }

  @Override
  public void onPause(boolean multitasking) {
    FirebasePlugin.inBackground = true;
  }

  @Override
  public void onResume(boolean multitasking) {
    FirebasePlugin.inBackground = false;
  }

  @Override
  public void onReset() {
    FirebasePlugin.notificationCallbackContext = null;
    FirebasePlugin.tokenRefreshCallbackContext = null;
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    final Bundle data = intent.getExtras();
    if (this.dynamicLinkCallback != null) {
      respondWithDynamicLink(intent);
    }
    if (data != null && data.containsKey("google.message_id")) {
      data.putBoolean("tap", true);
      FirebasePlugin.sendNotification(data, this.cordova.getActivity().getApplicationContext());
    }
  }

  public static boolean inBackground() {
    return FirebasePlugin.inBackground;
  }

  public static boolean hasNotificationsCallback() {
    return FirebasePlugin.notificationCallbackContext != null;
  }

  //
  // Cloud Messaging FCM
  //
  public static void sendNotification(Bundle bundle, Context context) {
    Log.d(TAG, "sendNotification called");
    if (!FirebasePlugin.hasNotificationsCallback()) {
      if (FirebasePlugin.notificationStack == null) {
        FirebasePlugin.notificationStack = new ArrayList<Bundle>();
      }
      notificationStack.add(bundle);

      Log.d(TAG, "sendNotification notificationStack.add");
      return;
    }

    final CallbackContext callbackContext = FirebasePlugin.notificationCallbackContext;
    if (callbackContext != null && bundle != null) {
      JSONObject json = new JSONObject();
      Set<String> keys = bundle.keySet();
      for (String key : keys) {
        try {
          json.put(key, bundle.get(key));
        } catch (JSONException e) {
          callbackContext.error(e.getMessage());
          return;
        }
      }

      PluginResult pluginresult = new PluginResult(PluginResult.Status.OK, json);
      pluginresult.setKeepCallback(true);
      callbackContext.sendPluginResult(pluginresult);
      Log.d(TAG, "sendNotification success");
    }
  }

  public static void sendToken(String token) {
    Log.d(TAG, "sendToken called");
    if (FirebasePlugin.tokenRefreshCallbackContext == null) {
      Log.d(TAG, "sendToken tokenRefreshCallbackContext null");
      return;
    }

    final CallbackContext callbackContext = FirebasePlugin.tokenRefreshCallbackContext;
    if (callbackContext != null && token != null) {
      PluginResult pluginresult = new PluginResult(PluginResult.Status.OK, token);
      pluginresult.setKeepCallback(true);
      callbackContext.sendPluginResult(pluginresult);
      Log.d(TAG, "sendToken success. token: " + token);
    }
  }

  private void onNotificationOpen(final CallbackContext callbackContext) {
    Log.d(TAG, "onNotificationOpen called");
    FirebasePlugin.notificationCallbackContext = callbackContext;
    if (FirebasePlugin.notificationStack != null) {
      for (Bundle bundle : FirebasePlugin.notificationStack) {
        FirebasePlugin.sendNotification(bundle, this.cordova.getActivity().getApplicationContext());
        Log.d(TAG, "onNotificationOpen sendNotification");
      }
      FirebasePlugin.notificationStack.clear();
      Log.d(TAG, "onNotificationOpen notificationStack.clear");
    }
  }

  private void onTokenRefresh(final CallbackContext callbackContext) {
    Log.d(TAG, "onTokenRefresh called");
    FirebasePlugin.tokenRefreshCallbackContext = callbackContext;

    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          String currentToken = FirebaseInstanceId.getInstance().getToken();
          if (currentToken != null) {
            FirebasePlugin.sendToken(currentToken);
            Log.d(TAG, "onTokenRefresh success. token: " + currentToken);
          }
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void getId(final CallbackContext callbackContext) {
    Log.d(TAG, "getId called");
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          String id = FirebaseInstanceId.getInstance().getId();
          callbackContext.success(id);
          Log.d(TAG, "getId success. id: " + id);
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void getToken(final CallbackContext callbackContext) {
    Log.d(TAG, "getToken called");
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          String token = FirebaseInstanceId.getInstance().getToken();
          callbackContext.success(token);
          Log.d(TAG, "getToken success. token: " + token);
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void hasPermission(final CallbackContext callbackContext) {
    Log.d(TAG, "hasPermission called");
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          Context context = cordova.getActivity();
          NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
          boolean areNotificationsEnabled = notificationManagerCompat.areNotificationsEnabled();
          JSONObject object = new JSONObject();
          object.put("isEnabled", areNotificationsEnabled);
          callbackContext.success(object);
          Log.d(TAG, "hasPermission success. areEnabled: " + (areNotificationsEnabled ? "true" : "false"));
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void setBadgeNumber(final CallbackContext callbackContext, final int number) {
    Log.d(TAG, "setBadgeNumber called. number: " + Integer.toString(number));
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          Context context = cordova.getActivity();
          SharedPreferences.Editor editor = context.getSharedPreferences(KEY, Context.MODE_PRIVATE).edit();
          editor.putInt(KEY, number);
          editor.apply();
          ShortcutBadger.applyCount(context, number);
          callbackContext.success();
          Log.d(TAG, "setBadgeNumber success");
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void getBadgeNumber(final CallbackContext callbackContext) {
    Log.d(TAG, "getBadgeNumber called");
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          Context context = cordova.getActivity();
          SharedPreferences settings = context.getSharedPreferences(KEY, Context.MODE_PRIVATE);
          int number = settings.getInt(KEY, 0);
          callbackContext.success(number);
          Log.d(TAG, "getBadgeNumber success. number: " + Integer.toString(number));
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }
  
  private void subscribe(final CallbackContext callbackContext, final String topic) {
    Log.d(TAG, "subscribe called. topic: " + topic);
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          FirebaseMessaging.getInstance().subscribeToTopic(topic);
          callbackContext.success();
          Log.d(TAG, "subscribe success");
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void unsubscribe(final CallbackContext callbackContext, final String topic) {
    Log.d(TAG, "unsubscribe called. topic: " + topic);
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          FirebaseMessaging.getInstance().unsubscribeFromTopic(topic);
          callbackContext.success();
          Log.d(TAG, "unsubscribe success");
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }
  
  private void unregister(final CallbackContext callbackContext) {
    Log.d(TAG, "unregister called");
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          FirebaseInstanceId.getInstance().deleteInstanceId();
          String currentToken = FirebaseInstanceId.getInstance().getToken();
          if (currentToken != null) {
            FirebasePlugin.sendToken(currentToken);
          }
          callbackContext.success();
          Log.d(TAG, "unregister success. currentToken: " + currentToken);
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void clearAllNotifications(final CallbackContext callbackContext) {
    Log.d(TAG, "clearAllNotifications called");
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          Context context = cordova.getActivity();
          NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
          nm.cancelAll();
          callbackContext.success();
          Log.d(TAG, "clearAllNotifications success");
        } catch (Exception e) {
        }
      }
    });
  }

  // 
  // Analytics
  //
  private void logEvent(final CallbackContext callbackContext, final String name, final JSONObject params) throws JSONException {
    Log.d(TAG, "logEvent called. name: " + name);
    final Bundle bundle = new Bundle();
    Iterator iter = params.keys();
    while (iter.hasNext()) {
      String key = (String) iter.next();
      Object value = params.get(key);

      if (value instanceof Integer || value instanceof Double) {
        bundle.putFloat(key, ((Number) value).floatValue());
      } else {
        bundle.putString(key, value.toString());
      }
    }

    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          mFirebaseAnalytics.logEvent(name, bundle);
          callbackContext.success();
          Log.d(TAG, "logEvent success");
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void setScreenName(final CallbackContext callbackContext, final String name) {
    Log.d(TAG, "setScreenName called. name: " + name);
    cordova.getActivity().runOnUiThread(new Runnable() {
      public void run() {
        try {
          mFirebaseAnalytics.setCurrentScreen(cordova.getActivity(), name, null);
          callbackContext.success();
          Log.d(TAG, "setScreenName success");
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void setUserId(final CallbackContext callbackContext, final String id) {
    Log.d(TAG, "setUserId called. id: " + id);
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          mFirebaseAnalytics.setUserId(id);
          callbackContext.success();
          Log.d(TAG, "setUserId success");
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void setUserProperty(final CallbackContext callbackContext, final String name, final String value) {
    Log.d(TAG, "setUserProperty called. name: " + name + " value: " + value);
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          mFirebaseAnalytics.setUserProperty(name, value);
          callbackContext.success();
          Log.d(TAG, "setUserProperty success");
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void setAnalyticsCollectionEnabled(final CallbackContext callbackContext, final boolean enabled) {
    Log.d(TAG, "setAnalyticsCollectionEnabled called. enabled: " + (enabled ? "true" : "false"));
    final FirebasePlugin self = this;
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          mFirebaseAnalytics.setAnalyticsCollectionEnabled(enabled);
          callbackContext.success();
          Log.d(TAG, "setAnalyticsCollectionEnabled success");
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
      }
    });
  }
  private static Map<String, Object> defaultsToMap(JSONObject object) throws JSONException {
    final Map<String, Object> map = new HashMap<String, Object>();

    for (Iterator<String> keys = object.keys(); keys.hasNext(); ) {
      String key = keys.next();
      Object value = object.get(key);

      if (value instanceof Integer) {
        //setDefaults() should take Longs
        value = new Long((Integer) value);
      } else if (value instanceof JSONArray) {
        JSONArray array = (JSONArray) value;
        if (array.length() == 1 && array.get(0) instanceof String) {
          //parse byte[] as Base64 String
          value = Base64.decode(array.getString(0), Base64.DEFAULT);
        } else {
          //parse byte[] as numeric array
          byte[] bytes = new byte[array.length()];
          for (int i = 0; i < array.length(); i++)
            bytes[i] = (byte) array.getInt(i);
          value = bytes;
        }
      }

      map.put(key, value);
    }
    return map;
  }
}
