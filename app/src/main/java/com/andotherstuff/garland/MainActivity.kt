package com.andotherstuff.garland

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.andotherstuff.garland.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var session: GarlandSessionStore
    private lateinit var store: LocalDocumentStore
    private lateinit var workScheduler: GarlandWorkScheduler

    private val composeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) refreshDocumentList()
    }

    private val configLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* settings saved — no visible change needed on main screen */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val v = resources.getDimensionPixelSize(R.dimen.garland_screen_padding)
            val side = resources.getDimensionPixelSize(R.dimen.garland_home_side_padding)
            binding.contentLayout.setPadding(bars.left + side, bars.top + v, bars.right + side, 0)
            binding.bottomBar.setPadding(bars.left + side, 0, bars.right + side, bars.bottom + v + resources.getDimensionPixelSize(R.dimen.garland_section_gap))
            binding.bottomBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
                val barHeight = bottom - top
                binding.documentListContainer.parent.let { sv ->
                    if (sv is android.widget.ScrollView) {
                        sv.setPadding(0, 0, 0, barHeight)
                    }
                }
            }
            insets
        }
        session = GarlandSessionStore(applicationContext)
        store = LocalDocumentStore(applicationContext)
        workScheduler = GarlandWorkScheduler(applicationContext)

        binding.createFileButton.setOnClickListener {
            composeLauncher.launch(ComposeActivity.createIntent(this))
        }

        binding.configButton.setOnClickListener {
            configLauncher.launch(ConfigActivity.createIntent(this))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDocumentList()
    }

    private fun refreshDocumentList() {
        val records = store.listDocuments().sortedByDescending { it.updatedAt }
        binding.documentListContainer.removeAllViews()

        if (records.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = getString(R.string.document_list_empty)
                setTextColor(ContextCompat.getColor(context, R.color.garland_muted))
            }
            binding.documentListContainer.addView(emptyView)
            return
        }

        records.forEach { record ->
            val planDecode = GarlandPlanInspector.decodeResult(store.readUploadPlan(record.documentId))
            val button = MaterialButton(
                android.view.ContextThemeWrapper(this, R.style.Widget_Garland_DocumentPickerButton),
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle,
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).also { params ->
                    params.bottomMargin = resources.getDimensionPixelSize(R.dimen.garland_list_item_gap)
                }
                isAllCaps = false
                textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                setPaddingRelative(
                    resources.getDimensionPixelSize(R.dimen.garland_card_padding),
                    resources.getDimensionPixelSize(R.dimen.garland_content_gap),
                    resources.getDimensionPixelSize(R.dimen.garland_card_padding),
                    resources.getDimensionPixelSize(R.dimen.garland_content_gap),
                )
                setLineSpacing(resources.getDimension(R.dimen.garland_tight_gap), 1f)
                text = DocumentDiagnosticsFormatter.listLabel(
                    record = record,
                    summary = planDecode.summary,
                    isSelected = false,
                    planMalformed = planDecode.malformed,
                )
                val bgColor = ContextCompat.getColor(context, R.color.garland_surface_soft)
                val strokeColor = ContextCompat.getColor(context, R.color.garland_outline_soft)
                val textColor = ContextCompat.getColor(context, R.color.garland_ink)
                backgroundTintList = ColorStateList.valueOf(bgColor)
                this.strokeColor = ColorStateList.valueOf(strokeColor)
                setTextColor(textColor)
                cornerRadius = resources.getDimensionPixelSize(R.dimen.garland_button_corner_radius)
                strokeWidth = resources.displayMetrics.density.toInt().coerceAtLeast(1)
                setOnClickListener {
                    startActivity(DiagnosticsActivity.createIntent(this@MainActivity, record.documentId))
                }
            }
            binding.documentListContainer.addView(button)
        }
    }
}
