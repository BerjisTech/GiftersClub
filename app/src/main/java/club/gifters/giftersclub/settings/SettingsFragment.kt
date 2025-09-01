package club.gifters.giftersclub.settings

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import club.gifters.giftersclub.R

/**
 * Settings screen using an iOS-style accordion of sections.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {
    companion object {
        private const val ARG_INITIAL_TAB = "initial_tab"
        fun newInstance(initialTab: Int = 0): SettingsFragment = SettingsFragment().apply {
            arguments = Bundle().apply { putInt(ARG_INITIAL_TAB, initialTab) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sections = listOf(
            Triple(
                view.findViewById<View>(R.id.sectionProfileHeader),
                view.findViewById<TextView>(R.id.caretProfile),
                view.findViewById<FrameLayout>(R.id.sectionProfileContent)
            ) to { ProfileSettingsFragment() },
            Triple(
                view.findViewById<View>(R.id.sectionSecurityHeader),
                view.findViewById<TextView>(R.id.caretSecurity),
                view.findViewById<FrameLayout>(R.id.sectionSecurityContent)
            ) to { SecuritySettingsFragment() },
            Triple(
                view.findViewById<View>(R.id.sectionModerationHeader),
                view.findViewById<TextView>(R.id.caretModeration),
                view.findViewById<FrameLayout>(R.id.sectionModerationContent)
            ) to { ModerationSettingsFragment() },
            Triple(
                view.findViewById<View>(R.id.sectionInteractionHeader),
                view.findViewById<TextView>(R.id.caretInteraction),
                view.findViewById<FrameLayout>(R.id.sectionInteractionContent)
            ) to { InteractionSettingsFragment() },
            Triple(
                view.findViewById<View>(R.id.sectionSubscriptionsHeader),
                view.findViewById<TextView>(R.id.caretSubscriptions),
                view.findViewById<FrameLayout>(R.id.sectionSubscriptionsContent)
            ) to { SubscriptionSettingsFragment() }
        )

        fun toggleSection(header: View, caret: TextView, container: FrameLayout, fragmentFactory: () -> Fragment) {
            val expanding = !container.isVisible
            container.isVisible = expanding
            caret.text = if (expanding) "v" else ">"
            if (expanding && childFragmentManager.findFragmentById(container.id) == null) {
                childFragmentManager.beginTransaction()
                    .replace(container.id, fragmentFactory())
                    .commit()
            }
        }

        sections.forEach { (triple, factory) ->
            val (header, caret, container) = triple
            header.setOnClickListener { toggleSection(header, caret, container, factory) }
        }

        // Expand the requested initial section (to keep deeplink behavior)
        val idx = arguments?.getInt(ARG_INITIAL_TAB, 0) ?: 0
        val safeIdx = idx.coerceIn(0, sections.lastIndex)
        val (triple, factory) = sections[safeIdx]
        val (header, caret, container) = triple
        // Expand only this one by default
        toggleSection(header, caret, container, factory)
    }
}
