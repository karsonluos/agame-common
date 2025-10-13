package com.robining.games.frame.dialogs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.MutableLiveData
import com.robining.games.frame.R
import kotlinx.coroutines.*

open class MiniLoadingDialog(val block: ((Dialog) -> Unit)? = null) : DialogFragment() {
    private var refCount = 0
    private var tvMessage: TextView? = null
    private val messageLiveData = MutableLiveData<CharSequence?>()
    private var scope = CoroutineScope(Dispatchers.Main)
    private var dismissJob: Job? = null
    var minShowTime: Long = 0L
    private var showAtTime: Long = 0

    @Volatile
    private var shouldDismiss = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (savedInstanceState != null) {
            //不允许重建
            dismissAllowingStateLoss()
        }
        return inflater.inflate(provideContentLayout(), container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvMessage = view.findViewById(R.id.tv_message)
        messageLiveData.observe(this) {
            val _tvMessage = tvMessage ?: return@observe
            if (it.isNullOrBlank()) {
                _tvMessage.visibility = View.GONE
            } else {
                _tvMessage.visibility = View.VISIBLE
                _tvMessage.text = it
            }
        }
    }

    fun setLoadingText(text: String): MiniLoadingDialog {
        messageLiveData.postValue(text)
        return this
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        block?.invoke(dialog)
        return dialog
    }

    protected fun provideContentLayout(): Int {
        return R.layout.dialog_loading_mini
    }

    protected fun provideDimAmount(): Float {
        return 0f
    }

    override fun onStart() {
        super.onStart()
        val window = dialog!!.window
        if (window != null) {
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val lp = window.attributes
            lp.dimAmount = provideDimAmount()
            window.attributes = lp
        }
    }

    override fun show(manager: FragmentManager, tag: String?) {
        showNow(manager, tag)
    }

    /**
     * @see MiniLoadingDialog.showNow
     */
    @Deprecated("")
    override fun show(transaction: FragmentTransaction, tag: String?): Int {
        throw IllegalStateException("don't allow use this method")
    }

    override fun showNow(manager: FragmentManager, tag: String?) {
        shouldDismiss = false
        showAtTime = SystemClock.elapsedRealtime()
        dismissJob?.cancel()
        dismissJob = null
        refCount++
        if (manager.findFragmentByTag(tag) == null && !isAdded) {
            try {
                super.showNow(manager, tag)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun dismiss() {
        dismissAllowingStateLoss()
    }

    fun dismissAlways() {
        refCount = 0
        dismissAllowingStateLoss()
    }

    override fun dismissAllowingStateLoss() {
        refCount--
        if (refCount < 0) {
            refCount = 0
        }
        if (refCount == 0) {
            shouldDismiss = true
            if (minShowTime > 0) {
                val diff = minShowTime - (SystemClock.elapsedRealtime() - showAtTime)
                if (diff > 0) {
                    dismissJob = scope.launch {
                        delay(diff)
                        if (shouldDismiss) {
                            //可能中间取消了
                            safeDismiss()
                        }
                    }
                } else {
                    safeDismiss()
                }
            } else {
                safeDismiss()
            }
        }
    }

    private fun safeDismiss() {
        try {
            super.dismissAllowingStateLoss()
        } catch (ignored: Exception) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }
}