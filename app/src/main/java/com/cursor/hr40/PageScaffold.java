package com.cursor.hr40;

import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public final class PageScaffold {
    private static final float TITLE_SP = 22f;
    private static final float SECTION_SP = 18f;
    private static final float BODY_SP = 15f;

    private PageScaffold() {
    }

    public static LinearLayout open(AppCompatActivity activity, String title) {
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 24);
        root.setPadding(padding, dp(activity, 32), padding, dp(activity, 32));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        stylePageTitle(activity, titleView);
        root.addView(titleView, matchWrap());

        scrollView.addView(root);
        activity.setContentView(scrollView);
        return root;
    }

    public static TextView sectionTitle(AppCompatActivity activity, LinearLayout root, String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setPadding(0, dp(activity, 16), 0, dp(activity, 8));
        styleSectionTitle(activity, view);
        root.addView(view, matchWrap());
        return view;
    }

    public static TextView bodyText(AppCompatActivity activity, LinearLayout root, String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        styleBody(activity, view);
        root.addView(view, matchWrap());
        return view;
    }

    public static MaterialButton actionButton(AppCompatActivity activity, String text, Runnable action) {
        MaterialButton button = new MaterialButton(activity);
        button.setText(text);
        styleButton(button);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public static void stylePageTitle(AppCompatActivity activity, TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_SP);
        view.setTextColor(activity.getColor(R.color.md_primary));
    }

    public static void styleSectionTitle(AppCompatActivity activity, TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, SECTION_SP);
        view.setTextColor(activity.getColor(android.R.color.black));
    }

    public static void styleBody(AppCompatActivity activity, TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_SP);
        view.setTextColor(activity.getColor(android.R.color.black));
    }

    public static void styleButton(MaterialButton button) {
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_SP);
    }

    private static int dp(AppCompatActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
