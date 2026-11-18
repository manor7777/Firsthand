package app.firsthand.ui

import android.content.ClipboardManager
import android.content.Intent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * The floating menu shown over selected text in the reader: Android's standard one (Copy, Select all) plus Share and
 * "Block keyword". Compose can't add items to its default menu, so this is the same floating ActionMode with our own items.
 * Share and Block need the selected text, which Compose only hands over by copying it, so they go through the clipboard.
 */
class FirsthandTextToolbar(private val view: View, private val onBlock: (String) -> Unit) : TextToolbar {
    private var actionMode: ActionMode? = null
    private var area = Rect.Zero
    private var onCopy: (() -> Unit)? = null
    private var onPaste: (() -> Unit)? = null
    private var onCut: (() -> Unit)? = null
    private var onSelectAll: (() -> Unit)? = null

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?,
    ) {
        area = rect; onCopy = onCopyRequested; onPaste = onPasteRequested; onCut = onCutRequested; onSelectAll = onSelectAllRequested
        if (actionMode == null) {
            status = TextToolbarStatus.Shown
            actionMode = view.startActionMode(Callback(), ActionMode.TYPE_FLOATING)
        } else actionMode?.invalidate()
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        actionMode?.finish()
        actionMode = null
    }

    /** The text currently selected, by asking Compose to copy it and reading it back. */
    private fun selectedText(): String? {
        onCopy?.invoke() ?: return null
        val cm = view.context.getSystemService(ClipboardManager::class.java) ?: return null
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(view.context)?.toString()?.takeIf { it.isNotBlank() }
    }

    private inner class Callback : ActionMode.Callback2() {
        private fun build(menu: Menu) {
            menu.clear()
            if (onCopy != null) menu.add(0, COPY, 0, "Copy")
            if (onCut != null) menu.add(0, CUT, 1, "Cut")
            if (onPaste != null) menu.add(0, PASTE, 2, "Paste")
            if (onSelectAll != null) menu.add(0, SELECT_ALL, 3, "Select all")
            if (onCopy != null) { menu.add(0, SHARE, 4, "Share"); menu.add(0, BLOCK, 5, "Block keyword") }
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean { build(menu); return true }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean { build(menu); return true }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                COPY -> onCopy?.invoke()
                CUT -> onCut?.invoke()
                PASTE -> onPaste?.invoke()
                SELECT_ALL -> onSelectAll?.invoke()
                SHARE -> selectedText()?.let { text ->
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    runCatching { view.context.startActivity(Intent.createChooser(send, null)) }
                }
                BLOCK -> selectedText()?.let(onBlock)
            }
            if (item.itemId != SELECT_ALL) mode.finish() // Select all keeps the menu so the user can then copy or block
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) { actionMode = null; status = TextToolbarStatus.Hidden }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
            outRect.set(area.left.toInt(), area.top.toInt(), area.right.toInt(), area.bottom.toInt())
        }
    }

    private companion object {
        const val COPY = 1; const val CUT = 2; const val PASTE = 3; const val SELECT_ALL = 4; const val SHARE = 5; const val BLOCK = 6
    }
}
