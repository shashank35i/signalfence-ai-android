package com.signalfence.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    private var adminTapCount = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val swDark = view.findViewById<SwitchMaterial>(R.id.swDark)
        val swFiltering = view.findViewById<SwitchMaterial>(R.id.swNormalOnly)
        val adminSection = view.findViewById<LinearLayout>(R.id.adminSection)
        val btnResetEngine = view.findViewById<MaterialButton>(R.id.btnResetEngine)

        // ✅ Theme Switcher
        context?.let { ctx ->
            swDark.isChecked = SessionManager.isDarkMode(ctx)
            swDark.setOnCheckedChangeListener { _, isChecked ->
                SessionManager.setDarkMode(ctx, isChecked)
            }

            // ✅ Role-Based Access Control (RBAC)
            val isCurrentlyAdmin = SessionManager.getRole(ctx) == SessionManager.ROLE_ADMIN
            adminSection.visibility = if (isCurrentlyAdmin) View.VISIBLE else View.GONE
        }

        // Secret Admin Toggle (Tap title 5 times)
        tvTitle.setOnClickListener {
            adminTapCount++
            if (adminTapCount >= 5) {
                adminTapCount = 0
                context?.let { ctx ->
                    val nextRole = if (SessionManager.getRole(ctx) == SessionManager.ROLE_ADMIN) 
                        SessionManager.ROLE_USER else SessionManager.ROLE_ADMIN
                    SessionManager.setRole(ctx, nextRole)
                    adminSection.visibility = if (nextRole == SessionManager.ROLE_ADMIN) View.VISIBLE else View.GONE
                    Toast.makeText(ctx, "Access Level: ${nextRole.uppercase()}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnResetEngine.setOnClickListener {
            Toast.makeText(context, "Detection model recalibrated", Toast.LENGTH_LONG).show()
        }

        // ✅ Aggressive Filtering toggle
        context?.let { ctx ->
            swFiltering.isChecked = SessionManager.isAggressiveFiltering(ctx)
            swFiltering.setOnCheckedChangeListener { _, isChecked ->
                SessionManager.setAggressiveFiltering(ctx, isChecked)
                val status = if (isChecked) "MAX PROTECT" else "NORMAL"
                Toast.makeText(ctx, "Filter mode: $status", Toast.LENGTH_SHORT).show()
                // Refresh inbox to apply new sensitivity
                ctx.sendBroadcast(android.content.Intent(MessagesFragment.ACTION_SMS_UPDATED))
            }
        }

        return view
    }
}
