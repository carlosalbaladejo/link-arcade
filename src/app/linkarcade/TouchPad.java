package app.linkarcade;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Color;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

final class TouchPad extends LinearLayout {
    interface Listener { void button(String id, boolean down); }

    TouchPad(Style style, Listener listener, boolean driving) {
        super(style.context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setMotionEventSplittingEnabled(true);
        setPadding(style.dp(12), 0, style.dp(12), style.dp(10));

        FrameLayout dpad = group(style);
        FrameLayout face = group(style);
        addView(dpad, new LayoutParams(style.dp(138), style.dp(138)));
        LinearLayout center = style.row();
        center.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        center.setMotionEventSplittingEnabled(true);
        addView(center, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        addView(face, new LayoutParams(style.dp(138), style.dp(138)));

        if (!driving) position(dpad, style, listener, "dpad_up", Glyph.UP, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        if (!driving) position(dpad, style, listener, "dpad_down", Glyph.DOWN, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        position(dpad, style, listener, "dpad_left", Glyph.LEFT, Gravity.CENTER_VERTICAL | Gravity.LEFT);
        position(dpad, style, listener, "dpad_right", Glyph.RIGHT, Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        position(face, style, listener, "north", Glyph.TRIANGLE, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        position(face, style, listener, "south", Glyph.CROSS, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        position(face, style, listener, "west", Glyph.SQUARE, Gravity.CENTER_VERTICAL | Gravity.LEFT);
        position(face, style, listener, "east", Glyph.CIRCLE, Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        for (String id : new String[]{"select", "start"}) {
            Button button = overlayButton(style, label(id));
            bind(button, listener, id);
            LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, style.dp(48));
            params.leftMargin = style.dp(2);
            params.rightMargin = style.dp(2);
            center.addView(button, params);
        }
    }

    private static FrameLayout group(Style style) {
        FrameLayout frame = new FrameLayout(style.context);
        frame.setMotionEventSplittingEnabled(true);
        return frame;
    }

    static Button overlayButton(Style style, String label) {
        Button button = style.button(label);
        int[] pressed = {android.R.attr.state_pressed};
        StateListDrawable background = new StateListDrawable();
        background.addState(pressed, style.panel(0xce245546, Style.MINT, 10));
        background.addState(new int[0], style.panel(0x65111e2b, 0xb6c1dcdf, 10));
        button.setBackground(background);
        button.setStateListAnimator(null);
        button.setTextColor(new ColorStateList(new int[][]{pressed, {}}, new int[]{Style.MINT, Style.TEXT}));
        button.setTextSize(13);
        button.setPadding(style.dp(9), 0, style.dp(9), 0);
        return button;
    }

    private void position(FrameLayout group, Style style, Listener listener, String id, Glyph glyph, int gravity) {
        Button button = new GlyphKey(style, glyph);
        bind(button, listener, id);
        group.addView(button, new FrameLayout.LayoutParams(style.dp(46), style.dp(46), gravity));
    }

    private void bind(Button button, Listener listener, String id) {
        button.setContentDescription(label(id));
        button.setFocusable(false);
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.setPressed(true);
                    listener.button(id, true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    listener.button(id, false);
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) view.performClick();
                    return true;
                default:
                    return true;
            }
        });
        button.setOnClickListener(view -> {
            if (!view.isInTouchMode()) {
                listener.button(id, true);
                listener.button(id, false);
            }
        });
    }

    static String label(String id) {
        if (id == null) return MainActivity.buttonLabel(null);
        switch (id) {
            case "north": return "Triangle · top";
            case "south": return "Cross · bottom";
            case "west": return "Square · left";
            case "east": return "Circle · right";
            default: return MainActivity.buttonLabel(id);
        }
    }

    private enum Glyph {
        UP(180), DOWN(0), LEFT(90), RIGHT(270),
        TRIANGLE(-1), CROSS(-1), SQUARE(-1), CIRCLE(-1);

        final int rotation;

        Glyph(int rotation) { this.rotation = rotation; }
    }

    private static final class GlyphKey extends Button {
        private final Glyph glyph;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        GlyphKey(Style style, Glyph glyph) {
            super(style.context);
            this.glyph = glyph;
            setBackgroundColor(Color.TRANSPARENT);
            setStateListAnimator(null);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            if (glyph.rotation >= 0) {
                path.moveTo(-11, -14);
                path.lineTo(11, -14);
                path.lineTo(11, 3);
                path.lineTo(0, 14);
                path.lineTo(-11, 3);
                path.close();
            } else if (glyph == Glyph.TRIANGLE) {
                path.moveTo(0, -11);
                path.lineTo(11, 8);
                path.lineTo(-11, 8);
                path.close();
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.translate(getWidth() / 2f, getHeight() / 2f);
            float scale = Math.min(getWidth(), getHeight()) / 46f;
            canvas.scale(scale, scale);
            if (glyph.rotation >= 0) canvas.rotate(glyph.rotation);

            boolean pressed = isPressed();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pressed ? 0x9927564c : 0x430e1b27);
            if (glyph.rotation >= 0) canvas.drawPath(path, paint);
            else canvas.drawCircle(0, 0, 19, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(pressed ? Style.MINT : 0xd9d9edf0);
            paint.setStrokeWidth(glyph.rotation >= 0 ? 1.65f : 1.15f);
            if (glyph.rotation >= 0) canvas.drawPath(path, paint);
            else {
                canvas.drawCircle(0, 0, 19, paint);
                paint.setStrokeWidth(2);
                switch (glyph) {
                    case TRIANGLE: canvas.drawPath(path, paint); break;
                    case SQUARE: canvas.drawRect(-8, -8, 8, 8, paint); break;
                    case CIRCLE: canvas.drawCircle(0, 0, 10, paint); break;
                    case CROSS:
                        canvas.drawLine(-8, -8, 8, 8, paint);
                        canvas.drawLine(-8, 8, 8, -8, paint);
                        break;
                    default: break;
                }
            }
            canvas.restore();
        }

        @Override protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }
    }
}
