package com.astanabesta.midrop;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Signal design system: near-black graphite (night) / warm off-white (day), one lime accent
 * reserved for live state, Bricolage Grotesque display + IBM Plex Sans body, 3/8/14 radius scale.
 * All colors are resources so day/night follows the system automatically.
 */
final class Ui {
    static final int R_CTL = 3, R_CARD = 8, R_HERO = 14;
    private Ui() { }

    static int c(Activity a, int id) { return a.getResources().getColor(id, a.getTheme()); }
    static int back(Activity a) { return c(a, R.color.mid_bg); }
    static int card(Activity a) { return c(a, R.color.mid_card); }
    static int line(Activity a) { return c(a, R.color.mid_line); }
    static int ink(Activity a) { return c(a, R.color.mid_ink); }
    static int muted(Activity a) { return c(a, R.color.mid_muted); }
    static int accent(Activity a) { return c(a, R.color.mid_accent); }
    static int onAccent(Activity a) { return c(a, R.color.mid_on_accent); }
    static int ok(Activity a) { return c(a, R.color.mid_ok); }
    static int err(Activity a) { return c(a, R.color.mid_err); }
    static boolean night(Activity a) { return a.getResources().getBoolean(R.bool.mid_night); }

    static int dp(Activity a, int n) { return (int)(a.getResources().getDisplayMetrics().density * n + .5f); }

    static Typeface display(Activity a, boolean heavy) {
        return a.getResources().getFont(heavy ? R.font.bricolage_grotesque_800 : R.font.bricolage_grotesque_700);
    }
    static Typeface body(Activity a, int weight) {
        switch (weight) {
            case 500: return a.getResources().getFont(R.font.ibm_plex_sans_500);
            case 600: return a.getResources().getFont(R.font.ibm_plex_sans_600);
            case 700: return a.getResources().getFont(R.font.ibm_plex_sans_700);
            default: return a.getResources().getFont(R.font.ibm_plex_sans_400);
        }
    }

    static GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }

    /** Display heading: Bricolage Grotesque, tight tracking. */
    static TextView displayText(Activity a, String value, int size, boolean heavy) {
        TextView v = bodyText(a, value, size, 0, ink(a));
        v.setTypeface(display(a, heavy));
        v.setLetterSpacing(-0.02f);
        return v;
    }
    static TextView bodyText(Activity a, String value, int size, int weight, int color) {
        TextView v = new TextView(a);
        v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setTypeface(body(a, weight));
        return v;
    }
    /** Small uppercase section label, letter-spaced. */
    static TextView sectionLabel(Activity a, String value) {
        TextView v = bodyText(a, value, 10, 700, muted(a));
        v.setLetterSpacing(0.14f);
        return v;
    }

    static void space(Activity a, LinearLayout root, int height) {
        View v = new View(a); root.addView(v, new LinearLayout.LayoutParams(1, dp(a, height)));
    }
    static Button button(Activity a, String label, boolean primary, Runnable action) {
        Button b = new Button(a);
        b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTypeface(body(a, 700));
        b.setTextColor(primary ? onAccent(a) : ink(a));
        b.setBackgroundColor(0);
        GradientDrawable bg = shape(primary ? accent(a) : card(a), dp(a, R_CTL));
        if (!primary) bg.setStroke(1, line(a));
        b.setBackground(bg);
        b.setMinHeight(dp(a, 50));
        b.setOnClickListener(v -> action.run());
        return b;
    }
    static void setupWindow(Activity a) {
        a.getWindow().setStatusBarColor(back(a));
        a.getWindow().setNavigationBarColor(back(a));
        int current = a.getWindow().getDecorView().getSystemUiVisibility();
        if (night(a)) {
            current &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        } else {
            current |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        a.getWindow().getDecorView().setSystemUiVisibility(current);
    }
    static LinearLayout column(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(back(a));
        return l;
    }
    static TextView centered(Activity a, String text, int size, int color, int weight) {
        TextView t = bodyText(a, text, size, weight, color);
        t.setGravity(Gravity.CENTER);
        return t;
    }
}
