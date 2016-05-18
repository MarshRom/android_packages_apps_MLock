package com.cyngn.keyguard;

public interface SecurityMessageDisplay {
    void hideBouncer(int i);

    void setMessage(int i, boolean z);

    void setMessage(int i, boolean z, Object... objArr);

    void setMessage(CharSequence charSequence, boolean z);

    void setTimeout(int i);

    void showBouncer(int i);
}
