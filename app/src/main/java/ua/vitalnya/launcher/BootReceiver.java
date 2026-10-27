package ua.vitalnya.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** Запускає Вітальню після ввімкнення приставки. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final PendingResult pr = goAsync();
        // невелика пауза, щоб система встигла завантажити свій лаунчер — потім відкриваємо свій поверх
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(app, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    app.startActivity(i);
                } catch (Throwable ignored) {
                } finally {
                    pr.finish();
                }
            }
        }, 4000);
    }
}
