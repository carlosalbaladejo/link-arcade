package app.linkarcade;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

import java.util.Set;

final class PadDiagram extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shell = new Path();
    private final float[] xs = {109, 109, 80, 138, 371, 399, 343, 371, 106, 374,
            106, 374, 179, 301, 208, 272};
    private final float[] ys = {94, 148, 121, 121, 148, 121, 121, 94, 53, 53,
            24, 24, 177, 177, 115, 115};
    private final String[] labels = {"↑", "↓", "←", "→", "×", "○", "□", "△", "L1", "R1",
            "L2", "R2", "L3", "R3", "SELECT", "START"};
    private final int[] faceColors = {0xff94bcff, 0xffff949b, 0xffefade7, 0xff82e6c5};
    private Set<String> held = java.util.Collections.emptySet();

    PadDiagram(Style style, String description) {
        super(style.context);
        setContentDescription(description);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.CENTER);
        shell.moveTo(85, 52);
        shell.cubicTo(59, 55, 47, 69, 41, 99);
        shell.lineTo(22, 218);
        shell.cubicTo(12, 265, 63, 277, 82, 245);
        shell.lineTo(115, 194);
        shell.cubicTo(128, 170, 144, 214, 169, 216);
        shell.cubicTo(200, 225, 218, 193, 220, 177);
        shell.lineTo(260, 177);
        shell.cubicTo(262, 193, 280, 225, 311, 216);
        shell.cubicTo(336, 214, 352, 170, 365, 194);
        shell.lineTo(398, 245);
        shell.cubicTo(417, 277, 468, 265, 458, 218);
        shell.lineTo(439, 99);
        shell.cubicTo(433, 69, 421, 55, 395, 52);
        shell.close();
    }

    void show(Set<String> pressed) {
        if (held.equals(pressed)) return;
        held = pressed;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth() / 480f, getHeight() / 270f);
        canvas.save();
        canvas.translate((getWidth() - 480 * scale) / 2, (getHeight() - 270 * scale) / 2);
        canvas.scale(scale, scale);
        paint.setColor(0xff26394c);
        canvas.drawPath(shell, paint);
        paint.setColor(0xff526d82);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawPath(shell, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Style.BG);
        canvas.drawCircle(109, 121, 49, paint);
        canvas.drawCircle(371, 121, 49, paint);
        drawButton(canvas, 10, 29, 12);
        drawButton(canvas, 11, 29, 12);
        drawButton(canvas, 8, 32, 12);
        drawButton(canvas, 9, 32, 12);
        for (int i = 0; i < 8; i++) drawButton(canvas, i, 12, 12);
        drawStick(canvas, 12, "left_stick");
        drawStick(canvas, 13, "right_stick");
        drawButton(canvas, 14, 28, 10);
        drawButton(canvas, 15, 28, 10);
        canvas.restore();
    }

    private void drawStick(Canvas canvas, int index, String prefix) {
        float x = xs[index], y = ys[index];
        paint.setColor(Style.BG);
        canvas.drawCircle(x, y, 32, paint);
        float dx = (held.contains(prefix + "_right") ? 1 : 0) - (held.contains(prefix + "_left") ? 1 : 0);
        float dy = (held.contains(prefix + "_down") ? 1 : 0) - (held.contains(prefix + "_up") ? 1 : 0);
        canvas.save();
        canvas.translate(dx * 9, dy * 9);
        drawButton(canvas, index, 19, 19, dx != 0 || dy != 0);
        canvas.restore();
    }

    private void drawButton(Canvas canvas, int index, float halfWidth, float halfHeight) {
        drawButton(canvas, index, halfWidth, halfHeight, false);
    }

    private void drawButton(Canvas canvas, int index, float halfWidth, float halfHeight, boolean moving) {
        boolean pressed = moving || held.contains(Controls.LAYOUT.get(index));
        float x = xs[index], y = ys[index];
        paint.setColor(pressed ? Style.MINT : 0xff101c2b);
        canvas.drawRoundRect(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight,
                halfHeight, halfHeight, paint);
        paint.setColor(pressed ? Style.INK : index >= 4 && index < 8 ? faceColors[index - 4] : Style.TEXT);
        paint.setTextSize(index < 8 ? 16 : index >= 14 ? 9 : 13);
        canvas.drawText(labels[index], x, y - (paint.ascent() + paint.descent()) / 2, paint);
    }
}
