package com.robining.games.frame.views

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

class SimpleVBViewHolder<T : ViewBinding>(val view: T) : RecyclerView.ViewHolder(view.root) {
    val context : Context
        get() {
            return this.itemView.context
        }
    companion object {
        inline fun <reified T : ViewBinding> create(view: T): SimpleVBViewHolder<T> {
            return SimpleVBViewHolder(view)
        }
    }
}