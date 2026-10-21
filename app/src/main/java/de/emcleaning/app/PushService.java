package de.emcleaning.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.HashMap;
import java.util.Map;

public class PushService extends FirebaseMessagingService {
    private static final String CHANNEL = "employee_updates";
    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager.class)
                .createNotificationChannel(new NotificationChannel(CHANNEL, "Mitarbeiter-App", NotificationManager.IMPORTANCE_DEFAULT));
    }
    static void register() {
        final var user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) return;
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            var current = FirebaseAuth.getInstance().getCurrentUser();
            if (current == null || !current.getUid().equals(user.getUid())) return;
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("platform", "android");
            FirebaseFirestore.getInstance().collection("pushRecipients").document(user.getUid())
                    .collection("devices").document(token).set(data);
        });
    }
    static void unregister() {
        final var user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token ->
                FirebaseFirestore.getInstance().collection("pushRecipients").document(user.getUid())
                        .collection("devices").document(token).delete());
        FirebaseMessaging.getInstance().deleteToken();
    }
    @Override public void onNewToken(String token) { register(); }
    @Override public void onMessageReceived(RemoteMessage message) {
        final var user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.getUid().equals(message.getData().get("recipientUid"))) return;
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        createChannel(this);
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        var notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("E-M Cleaning")
                .setContentText("Eine neue Aktualisierung ist verfügbar. Bitte in der App anmelden.")
                .setContentIntent(tap).setAutoCancel(true).build();
        getSystemService(NotificationManager.class).notify(message.getData().getOrDefault("eventId", "update"), 1, notification);
    }
}
