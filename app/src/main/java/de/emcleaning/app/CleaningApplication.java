package de.emcleaning.app;

import android.app.Application;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.storage.FirebaseStorage;

public class CleaningApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        // Initialize once per process, before any Activity or push service uses Firebase.
        FirebaseFirestore.getInstance().setFirestoreSettings(
                new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());
        if (BuildConfig.DEBUG) {
            FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099);
            FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080);
            FirebaseStorage.getInstance().useEmulator("10.0.2.2", 9199);
        }

    }
}
