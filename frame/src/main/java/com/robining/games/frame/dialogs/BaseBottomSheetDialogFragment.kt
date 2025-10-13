package com.robining.games.frame.dialogs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.robining.games.frame.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

open class BaseBottomSheetDialogFragment : BottomSheetDialogFragment(),
    CoroutineScope by CoroutineScope(Job() + Dispatchers.Default)  {
    protected open fun windowSetting(window: Window) {}
    protected open fun dialogSetting(dialog: Dialog) {}
    protected open fun onDialogShow() {}
    protected open fun onDialogDismiss(isCancel: Boolean) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TopCornerBottomSheetDialog)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            windowSetting(it)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener {
            onDialogShow()
        }
        dialog.setOnDismissListener {
            onDialogDismiss(false)
        }
        dialog.setOnCancelListener {
            onDialogDismiss(true)
        }
        dialogSetting(dialog)
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancel("dialog fragment destroy")
    }
}