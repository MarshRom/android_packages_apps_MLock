package com.cyngn.keyguard;

import com.android.internal.widget.LockPatternUtils;

public interface KeyguardSecurityView {
    public static final int SCREEN_ON = 1;
    public static final int VIEW_REVEALED = 2;

    KeyguardSecurityCallback getCallback();

    void hideBouncer(int i);

    boolean needsInput();

    void onPause();

    void onResume(int i);

    void reset();

    void setKeyguardCallback(KeyguardSecurityCallback keyguardSecurityCallback);

    void setLockPatternUtils(LockPatternUtils lockPatternUtils);

    void showBouncer(int i);

    void showUsabilityHint();
}
