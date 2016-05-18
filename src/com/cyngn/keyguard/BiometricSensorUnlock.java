package com.cyngn.keyguard;

import android.view.View;

interface BiometricSensorUnlock {
    void cleanUp();

    int getQuality();

    void initializeView(View view);

    boolean isRunning();

    boolean start();

    boolean stop();

    void stopAndShowBackup();
}
