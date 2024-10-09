package by.chemerisuk.cordova.firebase;

import static androidx.core.content.ContextCompat.getSystemService;
import static com.google.android.gms.tasks.Tasks.await;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

import by.chemerisuk.cordova.support.CordovaMethod;
import by.chemerisuk.cordova.support.ReflectiveCordovaPlugin;
import me.leolin.shortcutbadger.ShortcutBadger;

public class FirebaseMessagingPlugin extends ReflectiveCordovaPlugin {
    private static final String TAG = "FCMPlugin";

    private static FirebaseMessagingPlugin instance;
    private JSONObject lastBundle;
    private boolean isBackground = false;
    private boolean forceShow = true;
    private CallbackContext backgroundCallback;
    private CallbackContext foregroundCallback;
    private CallbackContext requestPermissionCallback;
    private CallbackContext tokenRefreshCallback;
    private FirebaseMessaging firebaseMessaging;
    private NotificationManager notificationManager;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @CordovaMethod
    private void clearNotifications(CallbackContext callbackContext) {
        notificationManager.cancelAll();
        callbackContext.success();
    }

    @CordovaMethod
    private void deleteToken(CallbackContext callbackContext) throws Exception {
        await(firebaseMessaging.deleteToken());
        callbackContext.success();
    }

    private JSONObject getNotificationData(Intent intent) {
        Bundle bundle = intent.getExtras();

        if (bundle == null) {
            return null;
        }

        if (!bundle.containsKey("google.message_id") && !bundle.containsKey("google.sent_time")) {
            return null;
        }

        try {
            JSONObject notificationData = new JSONObject();
            Set<String> keys = bundle.keySet();
            for (String key : keys) {
                notificationData.put(key, bundle.get(key));
            }
            return notificationData;
        } catch (JSONException e) {
            Log.e(TAG, "getNotificationData", e);
            return null;
        }
    }

    @CordovaMethod
    private void getBadge(CallbackContext callbackContext) {
        Context context = cordova.getActivity();
        SharedPreferences settings = context.getSharedPreferences("badge", Context.MODE_PRIVATE);
        callbackContext.success(settings.getInt("badge", 0));
    }

    @CordovaMethod
    private void getToken(CordovaArgs args, CallbackContext callbackContext) throws Exception {
        String type = args.getString(0);
        if (!type.isEmpty()) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String) null));
        } else {
            String fcmToken = await(firebaseMessaging.getToken());
            callbackContext.success(fcmToken);
        }
    }

    static boolean isForceShow() {
        return instance != null && instance.forceShow;
    }

    @CordovaMethod
    private void onBackgroundMessage(CallbackContext callbackContext) {
        instance.backgroundCallback = callbackContext;
    }
    
    @CordovaMethod
    private void onMessage(CallbackContext callbackContext) {
        instance.foregroundCallback = callbackContext;
    
        // Asegurarse de enviar los datos correctos cuando la app está en foreground
        if (lastBundle != null) {
            sendNotification(lastBundle, callbackContext);
            lastBundle = null;
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        JSONObject notificationData = getNotificationData(intent);
        if (instance != null && notificationData != null) {
            CallbackContext callbackContext = instance.isBackground ? instance.backgroundCallback : instance.foregroundCallback;
            sendNotification(notificationData, callbackContext);
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        this.isBackground = true;
    }

    @Override
    public void onResume(boolean multitasking) {
        this.isBackground = false;
    }

    @Override
    protected void pluginInitialize() {
        Context context = this.cordova.getActivity().getApplicationContext();
        if (!FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context);
        }
        FirebaseMessagingPlugin.instance = this;

        firebaseMessaging = FirebaseMessaging.getInstance();
        notificationManager = getSystemService(cordova.getActivity(), NotificationManager.class);
        lastBundle = getNotificationData(cordova.getActivity().getIntent());
    }

    @CordovaMethod
    private void requestPermission(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        JSONObject options = args.getJSONObject(0);
        Context context = cordova.getActivity().getApplicationContext();
        forceShow = options.optBoolean("forceShow");
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            callbackContext.success();
        } else {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    return;
                }
                int permission = cordova.getActivity().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS);
                if (permission == PackageManager.PERMISSION_GRANTED) {
                    callbackContext.success();
                    return;
                }
                this.requestPermissionCallback = callbackContext;
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } catch (Exception e) {
                e.printStackTrace();
                callbackContext.error("Notifications permission is not granted");
            }
        }
    }

    static void sendNotification(RemoteMessage remoteMessage) {
        JSONObject notificationData = new JSONObject(remoteMessage.getData());
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        try {
            if (notification != null) {
                notificationData.put("gcm", toJSON(notification));
            }
            notificationData.put("google.message_id", remoteMessage.getMessageId());
            notificationData.put("google.sent_time", remoteMessage.getSentTime());

            if (instance != null) {
                CallbackContext callbackContext = instance.isBackground ? instance.backgroundCallback
                        : instance.foregroundCallback;
                instance.sendNotification(notificationData, callbackContext);
            }
        } catch (JSONException e) {
            Log.e(TAG, "sendNotification", e);
        }
    }

    private void sendNotification(JSONObject notificationData, CallbackContext callbackContext) {
        if (callbackContext != null) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, notificationData);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
        }
    }

    static void sendToken(String instanceId) {
        if (instance != null) {
            if (instance.tokenRefreshCallback != null && instanceId != null) {
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, instanceId);
                pluginResult.setKeepCallback(true);
                instance.tokenRefreshCallback.sendPluginResult(pluginResult);
            }
        }
    }

    @CordovaMethod
    private void setBadge(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        int value = args.getInt(0);
        if (value >= 0) {
            Context context = cordova.getActivity().getApplicationContext();
            ShortcutBadger.applyCount(context, value);
            callbackContext.success();
        } else {
            callbackContext.error("Badge value can't be negative");
        }
    }

    @CordovaMethod
    private void subscribe(CordovaArgs args, final CallbackContext callbackContext) throws Exception {
        String topic = args.getString(0);
        await(firebaseMessaging.subscribeToTopic(topic));
        callbackContext.success();
    }

    private static JSONObject toJSON(RemoteMessage.Notification notification) throws JSONException {
        JSONObject result = new JSONObject()
                .put("body", notification.getBody())
                .put("clickAction", notification.getClickAction())
                .put("color", notification.getColor())
                .put("icon", notification.getIcon())
                .put("sound", notification.getSound())
                .put("tag", notification.getTag())
                .put("title", notification.getTitle());

        Uri imageUri = notification.getImageUrl();
        if (imageUri != null) {
            result.put("imageUrl", imageUri.toString());
        }

        return result;
    }

    @CordovaMethod
    private void unsubscribe(CordovaArgs args, CallbackContext callbackContext) throws Exception {
        String topic = args.getString(0);
        await(firebaseMessaging.unsubscribeFromTopic(topic));
        callbackContext.success();
    }

    @CordovaMethod
    private void onTokenRefresh(CallbackContext callbackContext) {
        instance.tokenRefreshCallback = callbackContext;
    }
}
