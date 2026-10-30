package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

/** Bundled static Black face: Cyrillic works offline on KitKat, without variable-font support. */
final class PosterTypography {
    private static Typeface display;
    static Typeface face(Context context) {
        if (display == null) display = Typeface.createFromAsset(context.getAssets(), "fonts/RobotoCondensed-Black.ttf");
        return display;
    }
    static void headline(TextView view, int size) {
        view.setTypeface(face(view.getContext()));
        view.setTextScaleX(size >= 60 ? .76f : .92f);
        view.setTextSize(size);
        view.setIncludeFontPadding(false);
    }
    /** Fit long station names/numbers without changing the layout or losing their ending. */
    static void fit(TextView view, int max, int min) {
        headline(view, max);
        view.setSingleLine(true);
        view.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            int available = r-l-view.getPaddingLeft()-view.getPaddingRight();
            if (available <= 0) return;
            float density = view.getResources().getDisplayMetrics().scaledDensity;
            float measured = view.getPaint().measureText(view.getText().toString());
            float size = view.getTextSize()/density;
            if (measured > available) view.setTextSize(Math.max(min, size*available/measured));
        });
    }
}
