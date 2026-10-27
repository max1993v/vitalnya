package ua.vitalnya.launcher;

import android.app.Activity;
import android.os.Bundle;

/** Службова «порожня» активність: тимчасово вмикається, щоб система знову спитала, який лаунчер головний. */
public class FakeHome extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
