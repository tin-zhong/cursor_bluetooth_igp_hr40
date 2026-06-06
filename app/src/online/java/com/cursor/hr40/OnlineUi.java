package com.cursor.hr40;

import android.util.TypedValue;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

public final class OnlineUi {
    public static final float TITLE_SP = 22f;
    public static final float SECTION_SP = 18f;
    public static final float BODY_SP = 15f;

    private OnlineUi() {
    }

    public static void stylePageTitle(TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_SP);
        view.setTextColor(view.getContext().getColor(R.color.md_primary));
    }

    public static void styleSectionTitle(TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, SECTION_SP);
        view.setTextColor(view.getContext().getColor(android.R.color.black));
    }

    public static void styleBody(TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_SP);
        view.setTextColor(view.getContext().getColor(android.R.color.black));
    }

    public static void styleButton(MaterialButton button) {
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_SP);
    }
}
