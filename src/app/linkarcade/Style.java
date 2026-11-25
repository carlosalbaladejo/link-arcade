package app.linkarcade;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Style {
    static final int BG = 0xff0b141f;
    static final int CARD = 0xff172538;
    static final int EDGE = 0xff30445a;
    static final int TEXT = 0xffedf5f7;
    static final int MUTED = 0xffb2c3d1;
    static final int MINT = 0xff90f2ce;
    static final int INK = 0xff0a2523;

    final Context context;

    Style(Context context) { this.context = context; }

    int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    TextView text(String label, int size, int color) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    LinearLayout column() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    LinearLayout row() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    GradientDrawable panel(int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (stroke != 0) shape.setStroke(dp(1), stroke);
        return shape;
    }

    Button button(String title) {
        Button button = new Button(context);
        button.setText(title);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(16), dp(6), dp(16), dp(6));
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        int[][] states = {{android.R.attr.state_pressed}, {android.R.attr.state_focused}, {}};
        StateListDrawable background = new StateListDrawable();
        background.addState(states[0], panel(MINT, MINT, 12));
        background.addState(states[1], panel(MINT, MINT, 12));
        background.addState(states[2], panel(CARD, EDGE, 12));
        button.setBackground(background);
        button.setTextColor(new ColorStateList(states, new int[]{INK, INK, TEXT}));
        button.setFocusable(true);
        return button;
    }

    void append(LinearLayout parent, android.view.View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(margin);
        parent.addView(child, params);
    }
}
