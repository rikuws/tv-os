package fi.koti.launcher;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Horizontal row that keeps a slice of the neighbouring tile visible on both sides of
 * the focused one, so a row that continues off-screen always shows that it does.
 * Scrolls only when focus would otherwise land inside the peek zone.
 */
final class PeekLayoutManager extends LinearLayoutManager {
    private final int peekPx;

    PeekLayoutManager(Context context, int peekPx) {
        super(context, HORIZONTAL, false);
        this.peekPx = peekPx;
        setItemPrefetchEnabled(true);
        setInitialPrefetchItemCount(3);
    }

    @Override
    public boolean requestChildRectangleOnScreen(RecyclerView parent, View child, Rect rect,
                                                 boolean immediate, boolean focusedChildVisible) {
        int left = child.getLeft();
        int right = child.getRight();
        int pos = getPosition(child);
        int minLeft = pos == 0 ? parent.getPaddingLeft() : parent.getPaddingLeft() + peekPx;
        int maxRight = pos == getItemCount() - 1
                ? parent.getWidth() - parent.getPaddingRight()
                : parent.getWidth() - parent.getPaddingRight() - peekPx;
        int dx = 0;
        if (left < minLeft) dx = left - minLeft;
        else if (right > maxRight) dx = right - maxRight;
        if (dx == 0) return false;
        if (immediate) parent.scrollBy(dx, 0);
        else parent.smoothScrollBy(dx, 0, Motion.SCROLL_INTERPOLATOR, Motion.SCROLL_MS);
        return true;
    }
}
