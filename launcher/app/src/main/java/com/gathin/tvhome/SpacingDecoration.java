package com.gathin.tvhome;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Gaps between tiles in the dock.
 *
 * <p>This lives here rather than as margins on the item view on purpose. A wrap_content item
 * measures to its content width <i>plus</i> its margins, so a margin-wrapped tile ends up in a box
 * wider than the tile inside it; the tile's {@code center_horizontal} gravity then pushes it off the
 * item's own left edge. The item and the tile no longer share a coordinate space, which silently
 * broke anything positioned from the tile's window coordinates. An item decoration changes the
 * spacing without changing what the item measures to, so the two stay in step.
 */
class SpacingDecoration extends RecyclerView.ItemDecoration {

    private final int gap;

    SpacingDecoration(int gap) {
        this.gap = gap;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        RecyclerView.LayoutManager lm = parent.getLayoutManager();
        if (!(lm instanceof androidx.recyclerview.widget.LinearLayoutManager)) return;
        int position = parent.getChildAdapterPosition(view);
        int count = lm.getItemCount();
        if (position == RecyclerView.NO_POSITION || count <= 1) return;
        int half = gap / 2;
        outRect.left = position > 0 ? half : 0;
        outRect.right = position < count - 1 ? half : 0;
    }
}