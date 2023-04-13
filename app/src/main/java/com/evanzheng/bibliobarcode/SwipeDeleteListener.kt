package com.evanzheng.bibliobarcode;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

// implementation of "SwipeToDeleteCallback", written by Zachery Osborn at https://medium.com/@zackcosborn/step-by-step-recyclerview-swipe-to-delete-and-undo-7bbae1fce27e
abstract class SwipeDeleteListener extends ItemTouchHelper.SimpleCallback {

    @SuppressWarnings("unused")
    SwipeDeleteListener(BibliographyAdapter calledAdapter) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
    }

    @Override
    public boolean onMove(@NotNull RecyclerView recyclerView, @NotNull RecyclerView.ViewHolder a, @NotNull RecyclerView.ViewHolder b) {
        return false;
    }
}
