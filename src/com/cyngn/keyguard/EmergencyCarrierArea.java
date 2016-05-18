package com.cyngn.keyguard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.LinearLayout;

public class EmergencyCarrierArea extends LinearLayout {
    private View mCarrierText;
    private EmergencyButton mEmergencyButton;

    public EmergencyCarrierArea(Context context) {
        super(context);
    }

    public EmergencyCarrierArea(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        if (!KeyguardUpdateMonitor.sIsMultiSimEnabled) {
            this.mCarrierText = findViewById(R.id.carrier_text);
        }
        this.mEmergencyButton = (EmergencyButton) findViewById(R.id.emergency_call_button);
        this.mEmergencyButton.setOnTouchListener(new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (EmergencyCarrierArea.this.mCarrierText == null) {
                    EmergencyCarrierArea.this.mCarrierText = EmergencyCarrierArea.this.findViewById(R.id.msim_keyguard_carrier_area);
                }
                switch (event.getAction()) {
                    case SlidingChallengeLayout.SCROLL_STATE_IDLE /*0*/:
                        EmergencyCarrierArea.this.mCarrierText.animate().alpha(0.0f);
                        break;
                    case SlidingChallengeLayout.SCROLL_STATE_DRAGGING /*1*/:
                        EmergencyCarrierArea.this.mCarrierText.animate().alpha(1.0f);
                        break;
                }
                return false;
            }
        });
    }
}
