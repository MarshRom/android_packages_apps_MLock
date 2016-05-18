package com.cyngn.keyguard;

public interface KeyguardSecurityCallback {
    void dismiss(boolean z);

    int getFailedAttempts();

    boolean isVerifyUnlockOnly();

    void reportFailedUnlockAttempt();

    void reportSuccessfulUnlockAttempt();

    void setOnDismissAction(OnDismissAction onDismissAction);

    void showBackupSecurity();

    void userActivity(long j);
}
