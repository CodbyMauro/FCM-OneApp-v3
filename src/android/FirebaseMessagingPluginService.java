package by.chemerisuk.cordova.firebase;

import static android.content.ContentResolver.SCHEME_ANDROID_RESOURCE;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import com.toyotaoneapp.nonproduction.MainActivity;


public class FirebaseMessagingPluginService extends FirebaseMessagingService {
    private static final String TAG = "FCMPluginService";

    public static final String ACTION_FCM_MESSAGE = "by.chemerisuk.cordova.firebase.ACTION_FCM_MESSAGE";
    public static final String EXTRA_FCM_MESSAGE = "by.chemerisuk.cordova.firebase.EXTRA_FCM_MESSAGE";
    public static final String ACTION_FCM_TOKEN = "by.chemerisuk.cordova.firebase.ACTION_FCM_TOKEN";
    public static final String EXTRA_FCM_TOKEN = "by.chemerisuk.cordova.firebase.EXTRA_FCM_TOKEN";
    public final static String NOTIFICATION_ICON_KEY = "com.google.firebase.messaging.default_notification_icon";
    public final static String NOTIFICATION_COLOR_KEY = "com.google.firebase.messaging.default_notification_color";
    public final static String NOTIFICATION_CHANNEL_KEY = "com.google.firebase.messaging.default_notification_channel_id";

    private LocalBroadcastManager broadcastManager;
    private NotificationManager notificationManager;
    private int defaultNotificationIcon;
    private int defaultNotificationColor;
    private String defaultNotificationChannel;

    @Override
    public void onCreate() {
        broadcastManager = LocalBroadcastManager.getInstance(this);
        notificationManager = ContextCompat.getSystemService(this, NotificationManager.class);
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getApplicationContext().getPackageName(), PackageManager.GET_META_DATA);
            defaultNotificationIcon = ai.metaData.getInt(NOTIFICATION_ICON_KEY, ai.icon);
            defaultNotificationChannel = ai.metaData.getString(NOTIFICATION_CHANNEL_KEY, "default");
            defaultNotificationColor = ContextCompat.getColor(this, ai.metaData.getInt(NOTIFICATION_COLOR_KEY));
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Failed to load meta-data", e);
        } catch (Resources.NotFoundException e) {
            Log.d(TAG, "Failed to load notification color", e);
        }
        // On Android O or greater we need to create a new notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel defaultChannel = notificationManager.getNotificationChannel(defaultNotificationChannel);
            if (defaultChannel == null) {
                notificationManager.createNotificationChannel(
                        new NotificationChannel(defaultNotificationChannel, "Firebase", NotificationManager.IMPORTANCE_HIGH));
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        FirebaseMessagingPlugin.sendToken(token);

        Intent intent = new Intent(ACTION_FCM_TOKEN);
        intent.putExtra(EXTRA_FCM_TOKEN, token);
        broadcastManager.sendBroadcast(intent);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        FirebaseMessagingPlugin.sendNotification(remoteMessage);

        Intent intent = new Intent(ACTION_FCM_MESSAGE);
        intent.putExtra(EXTRA_FCM_MESSAGE, remoteMessage);
        broadcastManager.sendBroadcast(intent);

        if (FirebaseMessagingPlugin.isForceShow()) {
            RemoteMessage.Notification notification = remoteMessage.getNotification();
            if (notification != null) {
                showAlert(notification);
            }
        }
    }

    private void showAlert(RemoteMessage.Notification notification) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
        }

        int notificationId = (int) System.currentTimeMillis(); // Genera un ID único
    
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getNotificationChannel(notification))
                .setSound(getNotificationSound(notification.getSound()))
                .setContentTitle(notification.getTitle())
                .setContentText(notification.getBody())
                .setAutoCancel(true) 
                .setGroup(notification.getTag())
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(defaultNotificationIcon)
                .setColor(defaultNotificationColor);
        
        notificationManager.notify(notificationId, builder.build());

    }

    private String getNotificationChannel(RemoteMessage.Notification notification) {
        String channel = notification.getChannelId();
        if (channel == null) {
            return defaultNotificationChannel;
        } else {
            return channel;
        }
    }

    private Uri getNotificationSound(String soundName) {
        if (soundName == null || soundName.isEmpty()) {
            return null;
        } else if (soundName.equals("default")) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        } else {
            return Uri.parse(SCHEME_ANDROID_RESOURCE + "://" + getApplicationContext().getPackageName() + "/raw/" + soundName);
        }
    }
}