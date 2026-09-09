package com.example.socialblock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.socialblock.databinding.ActivityMainBinding
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Egyszerűsítés kedvéért egyetlen, szerkeszthető idősáv ezen a vázon;
    // több idősávhoz bővítsd listává + RecyclerView-vá.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerApps.layoutManager = LinearLayoutManager(this)
        loadInstalledApps()

        binding.buttonGrantAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.buttonGrantUsageAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        binding.buttonGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        binding.buttonEnableDeviceAdmin.setOnClickListener {
            val adminComponent = ComponentName(this, BlockDeviceAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.device_admin_explanation)
                )
            }
            startActivity(intent)
        }

        binding.switchRequireExternalApproval.isChecked =
            ScheduleManager.requiresExternalApproval(this)
        binding.switchRequireExternalApproval.setOnCheckedChangeListener { _, checked ->
            ScheduleManager.setRequiresExternalApproval(this, checked)
        }

        binding.buttonSaveSchedule.setOnClickListener { saveSchedule() }

        loadExistingSchedule()
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val launchableApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.distinct().sorted()

        val currentlyBlocked = ScheduleManager.getBlockedPackages(this).toMutableSet()

        binding.recyclerApps.adapter = AppListAdapter(
            packageManager, launchableApps, currentlyBlocked
        ) { pkg, isChecked ->
            if (isChecked) currentlyBlocked.add(pkg) else currentlyBlocked.remove(pkg)
            ScheduleManager.setBlockedPackages(this, currentlyBlocked)
        }
    }

    private fun loadExistingSchedule() {
        val windows = ScheduleManager.getWindows(this)
        val w = windows.firstOrNull() ?: return
        binding.inputStartHour.setText(w.startHour.toString())
        binding.inputStartMinute.setText(w.startMinute.toString())
        binding.inputEndHour.setText(w.endHour.toString())
        binding.inputEndMinute.setText(w.endMinute.toString())
    }

    private fun saveSchedule() {
        val startHour = binding.inputStartHour.text.toString().toIntOrNull() ?: return
        val startMinute = binding.inputStartMinute.text.toString().toIntOrNull() ?: 0
        val endHour = binding.inputEndHour.text.toString().toIntOrNull() ?: return
        val endMinute = binding.inputEndMinute.text.toString().toIntOrNull() ?: 0

        // Egyszerűsítés: minden napra vonatkozik ezen a vázon.
        val allDays = setOf(
            Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
        )

        ScheduleManager.setWindows(
            this,
            listOf(TimeWindow(allDays, startHour, startMinute, endHour, endMinute))
        )
        binding.textSaveConfirmation.visibility = android.view.View.VISIBLE
    }
}
