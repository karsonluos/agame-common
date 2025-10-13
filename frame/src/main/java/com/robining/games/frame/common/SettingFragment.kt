package com.robining.games.frame.common

import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.karsonluos.aos.common.base.KsBaseFragment
import com.robining.games.frame.R
import com.robining.games.frame.databinding.FragmentSettingBinding
import com.robining.games.frame.databinding.ItemGamesBinding
import com.robining.games.frame.databinding.LayoutSettingItemBinding
import com.robining.games.frame.feedback.FeedBackManager
import com.robining.games.frame.ktx.dp2px
import com.robining.games.frame.startup.StartUpContext
import com.robining.games.frame.utils.AppUtil
import com.robining.games.frame.views.SimpleVBViewHolder

class SettingFragment : KsBaseFragment() {
    companion object {
        fun newInstance(): SettingFragment {
            return SettingFragment()
        }
    }

    private val mView by lazy {
        FragmentSettingBinding.inflate(layoutInflater)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return mView.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mView.rvContent.layoutManager = LinearLayoutManager(requireContext())
        val adapter = MoreGameAdapter()
        adapter.items.addAll(Game.values().filter {
            it.packageName != StartUpContext.context.packageName
        })
        mView.rvContent.adapter = adapter
        val to = requireContext().theme.obtainStyledAttributes(intArrayOf(R.attr.setting_divider))
        var dividerDrawable = to.getDrawable(0)
        to.recycle()
//        val dividerItemDecoration = DividerItemDecoration(requireContext(),DividerItemDecoration.VERTICAL)
//        dividerItemDecoration.setDrawable(dividerDrawable!!)
        val dividerPadding = dp2px(10f)
        dividerDrawable?.let {
            val dividerItemDecoration = object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    if (parent.getChildAdapterPosition(view) != 0) {
                        outRect.set(0, dividerDrawable.intrinsicHeight, 0, 0)
                    }
                }

                override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                    parent.children.forEach { child->
                        val adapterPosition = parent.getChildAdapterPosition(child)
                        if (adapterPosition != 0){
                            val bottom: Int = child.top
                            val top: Int = bottom - dividerDrawable.intrinsicHeight
                            dividerDrawable.setBounds(dividerPadding, top, parent.width - dividerPadding, bottom)
                            dividerDrawable.draw(c)
                        }
                    }
                }
            }
            mView.rvContent.addItemDecoration(dividerItemDecoration)
        }

        mView.vgBgm.visibility = if (GameContext.supportBgm) View.VISIBLE else View.GONE
        mView.sliderBgm.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                FeedBackManager.bgmVolume = value
            }
        }
        mView.sliderBgm.value = FeedBackManager.bgmVolume
        mView.sliderSound.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                FeedBackManager.soundVolume = value
            }
        }
        mView.sliderSound.value = FeedBackManager.soundVolume
        mView.switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            FeedBackManager.vibrateEnable = isChecked
        }
        mView.switchVibrate.isChecked = FeedBackManager.vibrateEnable
        mView.btnFeedback.setOnClickListener {
            AppUtil.jumpToMarket(requireContext())
        }
        mView.btnPrivacyPolicy.setOnClickListener {
            AppUtil.viewH5(GameCenter.URL_PRIVACY_POLICY)
        }
        mView.btnTermsService.setOnClickListener {
            AppUtil.viewH5(GameCenter.URL_TERMS_SERVICE)
        }
        mView.btnContactUs.setOnClickListener {
            AppUtil.viewH5(GameCenter.URL_CONTACT_US)
        }

        SettingConfig.datas.forEach { data ->
            val itemView = LayoutSettingItemBinding.inflate(layoutInflater, mView.llSetting, true)
            itemView.tvItem.setText(data.nameResId)
            itemView.btnItem.setOnClickListener {
                AppUtil.viewH5(data.url)
            }
        }
    }

    class MoreGameAdapter : RecyclerView.Adapter<SimpleVBViewHolder<ItemGamesBinding>>() {
        val items = mutableListOf<Game>()

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): SimpleVBViewHolder<ItemGamesBinding> {
            return SimpleVBViewHolder.create(
                ItemGamesBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: SimpleVBViewHolder<ItemGamesBinding>, position: Int) {
            val item = items[position]
            holder.view.ivIcon.setImageResource(item.iconResId)
            holder.view.tvTitle.setText(item.nameResId)
            holder.view.tvDesc.setText(item.introduceResId)
            holder.view.root.setOnClickListener {
                AppUtil.startAppOrInstall(it.context, item.packageName)
            }
        }

        override fun getItemCount(): Int {
            return items.size
        }
    }
}