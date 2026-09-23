@file:Suppress("DEPRECATION")

package io.github.nexalloy.activity

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import app.morphe.extension.shared.Utils
import app.morphe.extension.shared.settings.preference.about.MorpheAboutPreference
import io.github.libxposed.service.XposedService
import io.github.nexalloy.AppPatchInfo
import io.github.nexalloy.BuildConfig
import io.github.nexalloy.R
import io.github.nexalloy.appPatchConfigurations
import io.github.nexalloy.common.UpdateChecker
import io.github.nexalloy.patchlist.CustomPatchManager
import kotlin.system.exitProcess

class SettingsActivity : Activity(), SettingApplication.ServiceStateListener {

    private var mService: XposedService? = null
    private lateinit var aboutPreference: MorpheAboutPreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                onBackPressed()
            }
        }
        setContentView(R.layout.activity_settings)
        actionBar?.setDisplayShowHomeEnabled(true)

        Utils.setContext(this)
        aboutPreference = MorpheAboutPreference(this).apply {
            setTitle(R.string.about_title)
        }

        if (savedInstanceState != null) return

        fragmentManager.beginTransaction().replace(R.id.settings_container, SettingsFragment())
            .commit()
    }

    override fun onStart() {
        super.onStart()
        SettingApplication.addServiceStateListener(this, true)
    }

    override fun onStop() {
        SettingApplication.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        mService = service
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.xp_settings_menu, menu)
        menu.findItem(R.id.menu_disable_auto_check).isVisible = false
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val aliasName = ComponentName(this, SettingsActivity::class.java.name + "Alias")
        menu.findItem(R.id.menu_hide_icon).isChecked =
            packageManager.getComponentEnabledSetting(aliasName) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        val menuDisableAutoCheck = menu.findItem(R.id.menu_disable_auto_check)
        try {
            val prefs = mService!!.getRemotePreferences("prefs")
            menuDisableAutoCheck.isChecked =
                prefs.getBoolean("disable_auto_check_update", false)
            menuDisableAutoCheck.isVisible = true
        } catch (_: Throwable) {
            menuDisableAutoCheck.isVisible = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_about -> {
                aboutPreference.onPreferenceClickListener?.onPreferenceClick(aboutPreference)
                true
            }

            R.id.menu_hide_icon -> {
                val newChecked = !item.isChecked
                item.isChecked = newChecked
                val aliasName = ComponentName(this, SettingsActivity::class.java.name + "Alias")
                val status = if (newChecked) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                packageManager.setComponentEnabledSetting(
                    aliasName,
                    status,
                    PackageManager.DONT_KILL_APP
                )
                true
            }

            R.id.menu_disable_auto_check -> {
                val newChecked = !item.isChecked
                item.isChecked = newChecked
                mService!!.getRemotePreferences("prefs")
                    .edit().putBoolean("disable_auto_check_update", newChecked).apply()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishAndRemoveTask()
        exitProcess(0)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    class SettingsFragment : PreferenceFragment(), SettingApplication.ServiceStateListener {
        private var mService: XposedService? = null

        private var offPreference: Preference? = null
        private var customCategory: PreferenceCategory? = null
        private var onCategory: PreferenceCategory? = null

        private val REQUEST_CODE_PICK_PATCH_LIST = 1001

        fun AppPatchInfo.getPreference(): Preference {
            return Preference(context).apply {
                title = appName
                key = appName
                intent = Intent(context, AppPatchSettingsActivity::class.java).apply {
                    putExtra(AppPatchSettingsActivity.ARGUMENT_APP_NAME, appName)
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val rootScreen = preferenceManager.createPreferenceScreen(context)
            preferenceScreen = rootScreen

            Preference(context).apply {
                setSummary(R.string.slogan_summary)
                isEnabled = false
                rootScreen.addPreference(this)
            }

            Utils.setContext(context)

            Preference(context).apply {
                summary =
                    "This app uses code from Morphe. To learn more, visit https://morphe.software"
                intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://morphe.software"))
                rootScreen.addPreference(this)
            }

            Preference(context).apply {
                setTitle(R.string.faq_title)
                intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/NexAlloy/NexAlloy/wiki/Frequently-Asked-Questions")
                )
                rootScreen.addPreference(this)
            }

            addPreferencesFromResource(R.xml.license_prefs)

            Preference(context).apply {
                setTitle(R.string.check_for_update_title)
                summary =
                    """Current version: ${BuildConfig.VERSION_NAME} (${BuildConfig.COMMIT_HASH}) ${BuildConfig.BUILD_TYPE}
                       |Build Date: ${DateUtils.getRelativeTimeSpanString(BuildConfig.COMMIT_DATE * 1000)}""".trimMargin()
                setOnPreferenceClickListener {
                    UpdateChecker().apply {
                        setActivity(activity)
                        checkUpdate(silent = false)
                    }
                    true
                }
                rootScreen.addPreference(this)
            }
            UpdateChecker().apply {
                setActivity(activity)
                autoCheckUpdate()
            }

            updateDynamicUI(false)
        }

        private fun refreshUI() {
            val service = mService
            val isModuleActivated: Boolean = if (service == null) false else {
                try {
                    service.getRemotePreferences("prefs")
                    service.apiVersion
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            updateDynamicUI(isModuleActivated)
        }

        fun updateDynamicUI(on: Boolean) {
            val rootScreen = preferenceScreen ?: return
            if (customCategory != null) rootScreen.removePreference(customCategory)
            if (onCategory != null) rootScreen.removePreference(onCategory)
            if (offPreference != null) rootScreen.removePreference(offPreference)

            if (!on) {
                offPreference = Preference(context).apply {
                    setSummary(R.string.module_not_activated_summary)
                    isEnabled = false
                    rootScreen.addPreference(this)
                }
            } else {
                // Custom Patch List category
                customCategory = PreferenceCategory(context).apply {
                    setTitle(R.string.custom_patch_list_title)
                    rootScreen.addPreference(this)

                    val summaryInfo = CustomPatchManager.getSummary(context)

                    val statusPref = Preference(context).apply {
                        title = if (summaryInfo != null) "Custom List Active" else "Default List Active"
                        summary = if (summaryInfo != null) {
                            getString(R.string.custom_patch_list_source_custom, summaryInfo.fileName, summaryInfo.totalPatches)
                        } else {
                            getString(R.string.custom_patch_list_source_builtin)
                        }
                        isEnabled = false
                    }
                    this.addPreference(statusPref)

                    val loadPref = Preference(context).apply {
                        setTitle(R.string.custom_patch_list_load_title)
                        setSummary(R.string.custom_patch_list_load_summary)
                        setOnPreferenceClickListener {
                            openFilePicker()
                            true
                        }
                    }
                    this.addPreference(loadPref)

                    if (summaryInfo != null) {
                        val reloadPref = Preference(context).apply {
                            setTitle(R.string.custom_patch_list_reload_title)
                            setSummary(R.string.custom_patch_list_reload_summary)
                            setOnPreferenceClickListener {
                                reloadCustomPatchList()
                                true
                            }
                        }
                        this.addPreference(reloadPref)

                        val clearPref = Preference(context).apply {
                            setTitle(R.string.custom_patch_list_clear_title)
                            setSummary(R.string.custom_patch_list_clear_summary)
                            setOnPreferenceClickListener {
                                clearCustomPatchList()
                                true
                            }
                        }
                        this.addPreference(clearPref)
                    }
                }

                // Patch Selection category
                onCategory = PreferenceCategory(context).apply {
                    setTitle(R.string.patch_selection)

                    rootScreen.addPreference(this)

                    this.addPreference(Preference(context).apply {
                        setSummary(R.string.force_stop_to_apply_summary)
                        isEnabled = false
                    })

                    for (appPatchInfo in appPatchConfigurations) {
                        this.addPreference(appPatchInfo.getPreference())
                    }
                }
            }
        }

        private fun openFilePicker() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("application/json", "text/plain", "text/json", "*/*")
                )
            }
            try {
                startActivityForResult(intent, REQUEST_CODE_PICK_PATCH_LIST)
            } catch (e: Exception) {
                Toast.makeText(context, "No document picker available", Toast.LENGTH_SHORT).show()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == REQUEST_CODE_PICK_PATCH_LIST && resultCode == Activity.RESULT_OK) {
                val uri = data?.data ?: return
                loadPatchListFromUri(uri)
            }
        }

        private fun loadPatchListFromUri(uri: Uri) {
            val progressDialog = ProgressDialog(context).apply {
                setMessage(getString(R.string.custom_patch_list_reading))
                setCancelable(false)
                show()
            }

            Thread {
                val result = CustomPatchManager.loadFromUri(context, uri)
                activity?.runOnUiThread {
                    try {
                        progressDialog.dismiss()
                    } catch (_: Exception) {}

                    result.onSuccess { summary ->
                        val message = getString(
                            R.string.custom_patch_list_import_success,
                            summary.fileName,
                            summary.totalPatches,
                            summary.packageCount
                        )
                        AlertDialog.Builder(context)
                            .setTitle(R.string.custom_patch_list_title)
                            .setMessage(message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()

                        refreshUI()
                    }.onFailure { err ->
                        val errMsg = getString(
                            R.string.custom_patch_list_import_error,
                            err.localizedMessage ?: err.message ?: "Unknown error"
                        )
                        AlertDialog.Builder(context)
                            .setTitle(R.string.custom_patch_list_title)
                            .setMessage(errMsg)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
            }.start()
        }

        private fun reloadCustomPatchList() {
            val progressDialog = ProgressDialog(context).apply {
                setMessage(getString(R.string.custom_patch_list_reading))
                setCancelable(false)
                show()
            }

            Thread {
                val result = CustomPatchManager.reloadCurrent(context)
                activity?.runOnUiThread {
                    try {
                        progressDialog.dismiss()
                    } catch (_: Exception) {}

                    result.onSuccess { summary ->
                        val message = getString(
                            R.string.custom_patch_list_import_success,
                            summary.fileName,
                            summary.totalPatches,
                            summary.packageCount
                        )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        refreshUI()
                    }.onFailure { err ->
                        val errMsg = getString(
                            R.string.custom_patch_list_import_error,
                            err.localizedMessage ?: err.message ?: "Unknown error"
                        )
                        AlertDialog.Builder(context)
                            .setTitle(R.string.custom_patch_list_title)
                            .setMessage(errMsg)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
            }.start()
        }

        private fun clearCustomPatchList() {
            CustomPatchManager.clear(context)
            Toast.makeText(context, R.string.custom_patch_list_cleared, Toast.LENGTH_SHORT).show()
            refreshUI()
        }

        override fun onStart() {
            super.onStart()
            SettingApplication.addServiceStateListener(this, true)
        }

        override fun onStop() {
            SettingApplication.removeServiceStateListener(this)
            super.onStop()
        }

        override fun onServiceStateChanged(service: XposedService?) {
            mService = service

            activity?.runOnUiThread {
                refreshUI()
            }
        }
    }
}
