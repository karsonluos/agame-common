package com.robining.games.frame.dialogs

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import android.text.TextUtils
import android.view.*
import android.widget.TextView
import com.robining.games.frame.R
import com.robining.games.frame.databinding.PopupBottomSelectDialogBinding
import java.util.ArrayList

/**
 * 通用底部选择对话框
 */
class BottomSelectDialog : BaseDialogFragment() {
    private var adapter: RecyclerView.Adapter<*>? = null
    private var mView: PopupBottomSelectDialogBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mView = PopupBottomSelectDialogBinding.inflate(inflater)
        return mView!!.root
    }

    override fun windowSetting(window: Window) {
        super.windowSetting(window)
        window.setGravity(Gravity.BOTTOM)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.attributes.width = ViewGroup.LayoutParams.MATCH_PARENT
        window.attributes.height = ViewGroup.LayoutParams.WRAP_CONTENT
        window.setWindowAnimations(R.style.BottomSlideInoutAnimation)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mView = this.mView!!
        mView.rvContent.layoutManager = LinearLayoutManager(requireContext())
        if (adapter != null) {
            mView.rvContent.adapter = adapter
        }
        //添加分割线
        mView.rvContent.addItemDecoration(object : ItemDecoration() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                val childCount = parent.childCount
                for (i in 0 until childCount) {
                    val child = parent.getChildAt(i)
                    val adapterPos = parent.getChildAdapterPosition(child)
                    if (adapterPos != 0) {
                        c.drawLine(
                            child.left.toFloat(),
                            (child.top - 1).toFloat(),
                            child.right.toFloat(),
                            child.top.toFloat(),
                            paint
                        )
                    }
                }
            }

            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val position = parent.getChildAdapterPosition(view)
                if (position != 0) {
                    outRect.top = 1
                }
            }

            init {
                paint.color = Color.parseColor("#D5D5D5")
            }
        })
        mView.root.setOnClickListener { dismiss() }
        mView.btnCancel.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        isCancelable = true
    }

    /**
     * 设置数据源
     */
    fun setAdapter(adapter: RecyclerView.Adapter<*>?) {
        this.adapter = adapter
        mView?.rvContent?.adapter = adapter
    }

    /**
     * 设置基础数据源
     *
     * @param data   数据
     * @param config 数据转换器
     * @param <T>    数据源数据类型
    </T> */
    fun <T> setAdapterWith(data: Collection<T>?, config: Config<T>): SimpleAdapter<T> {
        val adapter = SimpleAdapter(data, config)
        setAdapter(adapter)
        return adapter
    }

    /**
     * 基础Adapter实现，仅显示一个标题文字
     *
     * @param <T>
     */
    class SimpleAdapter<T>(data: Collection<T>?, config: Config<T>) :
        RecyclerView.Adapter<SimpleAdapter.ViewHolder>() {
        private val data: MutableList<T> = ArrayList()
        private val config: Config<T>

        fun getData(): List<T> {
            return data
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.item_bottom_select_dialog, viewGroup, false)
            )
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
            val item = data[i]
            val content = config.convert(item)
            viewHolder.tvContent.text = content
            viewHolder.itemView.setOnClickListener { v: View? ->
                config.onClick(
                    viewHolder.adapterPosition,
                    item
                )
            }
            if (config is WithSubtitleConfig<*>) {
                val subtitle = (config as WithSubtitleConfig<T>).convertToSubtitle(item)
                if (TextUtils.isEmpty(subtitle)) {
                    viewHolder.tvSubContent.visibility = View.GONE
                } else {
                    viewHolder.tvSubContent.text = subtitle
                    viewHolder.tvSubContent.visibility = View.VISIBLE
                }
            } else {
                viewHolder.tvSubContent.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int {
            return data.size
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var tvContent: TextView = itemView.findViewById(R.id.tv_content)
            var tvSubContent: TextView = itemView.findViewById(R.id.tv_sub_content)
        }

        init {
            if (data != null) {
                this.data.addAll(data)
            }
            this.config = config
        }
    }

    interface Config<T> {
        fun convert(item: T): String
        fun onClick(position: Int, item: T)
    }

    abstract class SimpleTextConfig : Config<String> {
        override fun convert(item: String): String {
            return item
        }
    }

    interface WithSubtitleConfig<T> : Config<T> {
        fun convertToSubtitle(item: T): String
    }
}