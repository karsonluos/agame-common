package com.robining.games.frame.dialogs

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.method.MovementMethod
import android.text.style.ClickableSpan
import android.view.*
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.robining.games.frame.R
import com.robining.games.frame.common.Game
import com.robining.games.frame.common.GameCenter
import com.robining.games.frame.databinding.DialogPrivacyBinding
import com.robining.games.frame.managers.PrivacyManager
import com.robining.games.frame.utils.AppUtil

class PrivacyDialog(
    private val onCancel: (() -> Unit)? = null,
    private val onConfirm: (() -> Unit)? = null
) : BaseDialogFragment() {
    private val mView by lazy {
        DialogPrivacyBinding.inflate(layoutInflater)
    }

    override fun windowSetting(window: Window) {
        super.windowSetting(window)
        window.setGravity(Gravity.CENTER)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.attributes.width = ViewGroup.LayoutParams.MATCH_PARENT
        window.attributes.height = ViewGroup.LayoutParams.WRAP_CONTENT
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
        val game = Game.findByPackageName(requireContext().packageName)
        mView.tvAppName.text = getString(game?.nameResId ?: R.string.privacy_policy)
        val content = SpannableStringBuilder(getString(R.string.privacy_content))
        val privacyPolicyKeyword = getString(R.string.privacy_policy_keyword)
        val termsServiceKeyword = getString(R.string.privacy_terms_keyword)
        bindClickWithSpan(content, privacyPolicyKeyword, GameCenter.URL_PRIVACY_POLICY)
        bindClickWithSpan(content, termsServiceKeyword, GameCenter.URL_TERMS_SERVICE)
        mView.tvContent.text = content
        mView.tvContent.movementMethod = LinkMovementMethod.getInstance()

        mView.btnOk.setOnClickListener {
            PrivacyManager.onAgree()
            dismiss()
            onConfirm?.invoke()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        onCancel?.invoke()
        activity?.finish()
    }

    private fun bindClickWithSpan(content: SpannableStringBuilder, keyword: String, url: String) {
        val startIndex = content.indexOf(keyword)
        val endIndex = startIndex + keyword.length
        content.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                AppUtil.viewH5(url)
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
            }
        }, startIndex, endIndex, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
    }

    override fun show(manager: FragmentManager, tag: String?) {
        if (PrivacyManager.isAgree()){
            onConfirm?.invoke()
            return
        }
        super.show(manager, tag)
    }

    override fun show(transaction: FragmentTransaction, tag: String?): Int {
        if (PrivacyManager.isAgree()){
            onConfirm?.invoke()
            return -1
        }
        return super.show(transaction, tag)
    }

    override fun showNow(manager: FragmentManager, tag: String?) {
        if (PrivacyManager.isAgree()){
            onConfirm?.invoke()
            return
        }
        super.showNow(manager, tag)
    }
}