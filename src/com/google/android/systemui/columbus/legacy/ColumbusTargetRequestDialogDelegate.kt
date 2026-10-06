package com.google.android.systemui.columbus.legacy

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.google.android.systemui.res.R
import javax.inject.Inject

class ColumbusTargetRequestDialogDelegate
@Inject
constructor(private val systemUIDialogFactory: SystemUIDialog.Factory) : SystemUIDialog.Delegate {
    lateinit var title: TextView
        private set

    lateinit var content: TextView
        private set

    lateinit var positiveButton: Button
        private set

    lateinit var negativeButton: Button
        private set

    override fun createDialog(): SystemUIDialog = systemUIDialogFactory.create(this)

    override fun onCreate(dialog: SystemUIDialog, savedInstanceState: Bundle?) {
        dialog.setContentView(R.layout.columbus_target_request_dialog)
        title = dialog.requireViewById(R.id.title)
        content = dialog.requireViewById(R.id.content)
        positiveButton = dialog.requireViewById(R.id.positive_button)
        negativeButton = dialog.requireViewById(R.id.negative_button)
    }
}
