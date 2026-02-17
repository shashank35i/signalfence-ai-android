package com.signalfence.app

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var tvSkip: TextView
    private lateinit var dots: LinearLayout
    private lateinit var btnContinue: MaterialButton

    private val pages = listOf(
        R.layout.item_onboard_1,
        R.layout.item_onboard_2,
        R.layout.item_onboard_3,
        R.layout.item_onboard_4
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        supportActionBar?.hide()

        pager = findViewById(R.id.pager)
        tvSkip = findViewById(R.id.tvSkip)
        dots = findViewById(R.id.dots)
        btnContinue = findViewById(R.id.btnContinue)

        pager.adapter = OnboardingPagerAdapter(pages)

        setupDots(pages.size)
        updateUiForPage(0)

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUiForPage(position)
            }
        })

        tvSkip.setOnClickListener {
            pager.setCurrentItem(pages.size - 1, true)
        }

        btnContinue.setOnClickListener {
            val pos = pager.currentItem
            if (pos < pages.size - 1) {
                pager.setCurrentItem(pos + 1, true)
            } else {
                finishOnboarding()
            }
        }
    }

    private fun finishOnboarding() {
        SessionManager.setFirstLaunchDone(this)

        val i = Intent(this, RequestsActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }


    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::pager.isInitialized && pager.currentItem > 0) {
            pager.setCurrentItem(pager.currentItem - 1, true)
        } else {
            super.onBackPressed()
        }
    }

    private fun setupDots(count: Int) {
        dots.removeAllViews()
        repeat(count) {
            val v = View(this)
            val lp = LinearLayout.LayoutParams(dp(8), dp(8))
            if (it != 0) lp.marginStart = dp(10)
            v.layoutParams = lp
            v.background = DotDrawable.inactive()
            dots.addView(v)
        }
    }

    private fun updateUiForPage(position: Int) {
        tvSkip.visibility = if (position == pages.size - 1) View.INVISIBLE else View.VISIBLE
        btnContinue.text = if (position == pages.size - 1) "Get Started" else "Continue"

        for (i in 0 until dots.childCount) {
            val v = dots.getChildAt(i)
            val lp = v.layoutParams as LinearLayout.LayoutParams
            if (i == position) {
                lp.width = dp(44)
                lp.height = dp(8)
                v.background = DotDrawable.active()
            } else {
                lp.width = dp(8)
                lp.height = dp(8)
                v.background = DotDrawable.inactive()
            }
            v.layoutParams = lp
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
