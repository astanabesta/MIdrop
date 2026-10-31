package com.astanabesta.midrop;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small, native visual system for the prototype; no external assets or libraries. */
final class Ui {
    static final int BACK = Color.rgb(248,250,255), INK = Color.rgb(20,33,59), MUTED = Color.rgb(112,128,151), BLUE = Color.rgb(69,109,242);
    private Ui() { }
    static int dp(Activity a, int n) { return (int)(a.getResources().getDisplayMetrics().density * n + .5f); }
    static GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d; }
    static TextView text(Activity a, String value, int size, int color, boolean bold) {
        TextView v = new TextView(a); v.setText(value); v.setTextSize(size); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v;
    }
    static void space(Activity a, LinearLayout root, int height) { View v = new View(a); root.addView(v, new LinearLayout.LayoutParams(1,dp(a,height))); }
    static Button button(Activity a, String label, boolean primary, Runnable action) {
        Button b = new Button(a); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.WHITE : INK); b.setBackground(shape(primary ? BLUE : Color.rgb(234,240,250),dp(a,16)));
        b.setMinHeight(dp(a,50)); b.setOnClickListener(v -> action.run()); return b;
    }
    static void setupWindow(Activity a) {
        a.getWindow().setStatusBarColor(BACK); a.getWindow().setNavigationBarColor(BACK);
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    static LinearLayout column(Activity a) { LinearLayout l = new LinearLayout(a); l.setOrientation(LinearLayout.VERTICAL); l.setBackgroundColor(BACK); return l; }
    static TextView centered(Activity a, String text, int size, int color, boolean bold) { TextView t = text(a,text,size,color,bold); t.setGravity(Gravity.CENTER); return t; }
}
