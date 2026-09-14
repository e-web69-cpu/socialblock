package com.example.socialblock

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.socialblock.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

            private lateinit var binding: ActivityMainBinding
            private lateinit var appListAdapter: AppListAdapter

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

                                                                                                    binding.switchBlockingEnabled.isChecked = ScheduleManager.isSimpleBlockingEnabled(this)
                                                                                                            binding.switchBlockingEnabled.setOnCheckedChangeListener { _, checked ->
                                                                                                                                ScheduleManager.setSimpleBlockingEnabled(this, checked)
                                                                                                            }
                                                                                                            
                                                                                                                    binding.inputSearchApps.addTextChangedListener(object : TextWatcher {
                                                                                                                                        override fun afterTextChanged(s: Editable?) {
                                                                                                                                                                appListAdapter.filter(s?.toString().orEmpty())
                                                                                                                                        }
                                                                                                                                                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                                                                                                                                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                                                                                                    })
            }

                    override fun onResume() {
                                super.onResume()
                                        refreshPermissionState()
                }

                    private fun refreshPermissionState() {
                                    setRowState(binding.rowAccessibility, binding.buttonGrantAccessibility, binding.checkAccessibility, isAccessibilityServiceEnabled())
                                            setRowState(binding.rowUsage, binding.buttonGrantUsageAccess, binding.checkUsage, isUsageAccessGranted())
                                                    setRowState(binding.rowOverlay, binding.buttonGrantOverlay, binding.checkOverlay, Settings.canDrawOverlays(this))
                                                            setRowState(binding.rowDeviceAdmin, binding.buttonEnableDeviceAdmin, binding.checkDeviceAdmin, isDeviceAdminActive())
                    }

                        private fun setRowState(
                                        row: android.widget.LinearLayout,
                                        button: android.widget.Button,
                                        check: android.widget.TextView,
                                        granted: Boolean
                                    ) {
                                        button.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
                                        check.visibility = if (granted) android.view.View.VISIBLE else android.view.View.GONE
                        }

                            private fun isAccessibilityServiceEnabled(): Boolean {
                                            val expected = "$packageName/${BlockAccessibilityService::class.java.canonicalName}"
                                            val enabledServices = Settings.Secure.getString(
                                                                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                                                            ) ?: return false
                                            return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
                            }

                                private fun isUsageAccessGranted(): Boolean {
                                                val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                                                val mode = appOps.checkOpNoThrow(
                                                                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
                                                                )
                                                        return mode == AppOpsManager.MODE_ALLOWED
                                }

                                    private fun isDeviceAdminActive(): Boolean {
                                                    val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                                                    val adminComponent = ComponentName(this, BlockDeviceAdminReceiver::class.java)
                                                            return dpm.isAdminActive(adminComponent)
                                    }

                                        private fun loadInstalledApps() {
                                                        val pm = packageManager
                                                        val launchableApps = pm.queryIntentActivities(
                                                                            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
                                                                        ).map { it.activityInfo.packageName }.distinct().sorted()

                                                                val selected = ScheduleManager.getSimpleBlockedPackage(this)

                                                                        appListAdapter = AppListAdapter(
                                                                                            packageManager, launchableApps, selected
                                                                                        ) { pkg ->
                                                                                            ScheduleManager.setSimpleBlockedPackage(this, pkg)
                                                                        }
                                                                                binding.recyclerApps.adapter = appListAdapter
                                        }
}
