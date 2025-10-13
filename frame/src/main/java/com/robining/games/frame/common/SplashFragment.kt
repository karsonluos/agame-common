package com.robining.games.frame.common

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.MutableLiveData
import cn.karsonluos.aos.common.base.KsBaseFragment
import com.robining.games.frame.R
import com.robining.games.frame.databinding.LayoutSplashBinding
import com.robining.games.frame.managers.PrivacyManager
import com.robining.games.frame.managers.UmpGdprManager

class SplashFragment : KsBaseFragment() {
    private val mView by lazy {
        LayoutSplashBinding.inflate(layoutInflater)
    }

    private val mProgressLiveData = MutableLiveData(0.0f)
    private val mProgressMessageLiveData by lazy {
        MutableLiveData<CharSequence>(getString(R.string.splash_initing_system))
    }
    private var progressOvered = false


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return mView.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity()
        Log.d("SplashActivity", "launch SplashActivity,isTaskRoot:${activity.isTaskRoot}")
        if (!activity.isTaskRoot) {
            activity.finish()
            return
        }

        mView.pbProgress.max = 10000
        mProgressLiveData.observe(viewLifecycleOwner) {
            val progress = it.coerceAtMost(1.0f)
            mView.pbProgress.progress = (progress * mView.pbProgress.max).toInt()
            if (progress >= 1.0f && !progressOvered) {
                progressOvered = true
                toNext()
                return@observe
            }
        }
        mProgressMessageLiveData.observe(viewLifecycleOwner) {
            mView.tvPbMessage.text = it
        }

        (SplashManager.overrideLogoResId
            ?: Game.findByPackageName(activity.packageName)?.iconResId)?.let {
            mView.ivIcon.setImageResource(it)
        }

        UmpGdprManager.request(requireActivity()) {
            if (it){
                PrivacyManager.onAgree()
            }
            toNext()
        }
    }

    private fun toNext() {
        (SplashManager.launchActivity ?: GameContext.launchActivity)?.let { activity ->
            startActivity(
                Intent(
                    requireContext(),
                    activity
                )
            )
        }
        activity?.finish()
    }
}