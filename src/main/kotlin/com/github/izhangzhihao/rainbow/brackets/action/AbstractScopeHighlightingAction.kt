package com.github.izhangzhihao.rainbow.brackets.action

import com.github.izhangzhihao.rainbow.brackets.RainbowInfo
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.textarea.TextComponentEditor
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.awt.Color
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

/**
 * AbstractScopeHighlightingAction
 *
 * Created by Yii.Guxing on 2018/05/17
 */
abstract class AbstractScopeHighlightingAction : AnAction() {

    final override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.editor.let { it != null && it !is TextComponentEditor }
    }

    final override fun actionPerformed(e: AnActionEvent) {
        val editor = e.editor ?: return
        val project = editor.project ?: return
        val psiFile = project.let { PsiDocumentManager.getInstance(it).getPsiFile(editor.document) } ?: return
        val offset = editor.caretModel.offset
        val rainbowInfo = psiFile.findRainbowInfoAt(offset) ?: return
        val highlightManager = HighlightManager.getInstance(project)
        val highlighters = editor.addHighlighter(highlightManager, rainbowInfo)

        editor.highlightingDisposer?.dispose()
        if (highlighters.isNotEmpty()) {
            editor.highlightingDisposer = HighlightingDisposer(editor) {
                editor.highlightingDisposer = null
                highlighters.forEach { highlightManager.removeSegmentHighlighter(editor, it) }
            }
        }
    }

    protected abstract fun Editor.addHighlighter(highlightManager: HighlightManager,
                                                 rainbowInfo: RainbowInfo): Collection<RangeHighlighter>

    private class HighlightingDisposer(private val editor: Editor,
                                       private val disposeAction: () -> Unit) : KeyAdapter(), FocusListener {

        init {
            editor.contentComponent.let {
                it.addFocusListener(this)
                it.addKeyListener(this)
            }
        }

        fun dispose() {
            disposeAction()
            editor.contentComponent.let {
                it.removeFocusListener(this)
                it.removeKeyListener(this)
            }
        }

        override fun focusGained(e: FocusEvent) = Unit
        override fun focusLost(e: FocusEvent) = dispose()
        override fun keyReleased(e: KeyEvent) = dispose()
    }

    companion object {
        private val HIGHLIGHTING_DISPOSER_KEY: Key<HighlightingDisposer> = Key.create("HIGHLIGHTING_DISPOSER_KEY")

        private var Editor.highlightingDisposer: HighlightingDisposer?
            get() = HIGHLIGHTING_DISPOSER_KEY[this]
            set(value) {
                HIGHLIGHTING_DISPOSER_KEY[this] = value
            }

        private val AnActionEvent.editor: Editor? get() = CommonDataKeys.EDITOR.getData(dataContext)

        private fun PsiElement.getRainbowInfo(offset: Int): RainbowInfo? {
            return RainbowInfo.RAINBOW_INFO_KEY[this]?.takeIf { it.containsOffset(offset) }
        }

        private fun PsiFile.findRainbowInfoAt(offset: Int): RainbowInfo? {
            var element = findElementAt(offset)
            while (element != null) {
                element.getRainbowInfo(offset)?.let { return it }
                element = element.parent
            }

            return null
        }

        fun Color.alphaBlend(background: Color, alpha: Float): Color {
            require(alpha in 0.0..1.0) { "alpha(0.0 <= alpha <= 1.0): $alpha" }

            val r = (1 - alpha) * background.red + alpha * red
            val g = (1 - alpha) * background.green + alpha * green
            val b = (1 - alpha) * background.blue + alpha * blue

            return Color(r.toInt(), g.toInt(), b.toInt())
        }
    }
}