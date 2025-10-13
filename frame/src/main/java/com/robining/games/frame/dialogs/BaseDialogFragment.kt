package com.robining.games.frame.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.Window
import androidx.appcompat.app.AppCompatDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

open class BaseDialogFragment : AppCompatDialogFragment(),
    CoroutineScope by CoroutineScope(Job() + Dispatchers.Default)  {
    private var isCanceled = false
    protected open fun windowSetting(window: Window) {}
    protected open fun dialogSetting(dialog: Dialog) {}
    protected open fun onDialogShow() {}
    protected open fun onDialogDismiss(isCancel: Boolean) {}

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.let { windowSetting(it) }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener {
            isCanceled = false
            onDialogShow()
        }
        dialogSetting(dialog)
        return dialog
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        isCanceled = true
        onDialogDismiss(true)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!isCanceled){
            onDialogDismiss(false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancel("dialog fragment destroy")
    }
}