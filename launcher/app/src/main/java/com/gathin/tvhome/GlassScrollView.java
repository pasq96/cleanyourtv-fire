package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.ScrollView;

/** ScrollView that leaves focus scrolling to its owner, which scrolls smoothly and keeps the headings in view. */
public final class GlassScrollView extends ScrollView {
    public GlassScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        return 0;
    }
}
