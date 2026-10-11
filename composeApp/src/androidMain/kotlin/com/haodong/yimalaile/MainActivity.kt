package com.haodong.yimalaile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.haodong.yimalaile.di.AppComponent
import com.haodong.yimalaile.di.create
import com.haodong.yimalaile.domain.export.AndroidReportExportService
import com.haodong.yimalaile.infrastructure.persistence.appDataStore
import com.haodong.yimalaile.notifications.AndroidNotificationScheduler
import com.viktormykhailiv.kmp.health.HealthManagerFactory
import com.viktormykhailiv.kmp.health.HealthManagerFactoryOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        AndroidNotificationScheduler.currentActivityHolder.set(this)

        val dataStore = appDataStore(this)
        val scheduler = AndroidNotificationScheduler(applicationContext)
        val reportExportService = AndroidReportExportService(applicationContext)
        val healthManager = HealthManagerFactory()
            .createManager(options = HealthManagerFactoryOptions.default())
        val component = AppComponent.create(dataStore, scheduler, reportExportService, healthManager)

        setContent {
            App(component)
        }
    }

    override fun onDestroy() {
        AndroidNotificationScheduler.currentActivityHolder.compareAndSet(this, null)
        super.onDestroy()
    }

    @Deprecated("Using legacy permission API bridged to NotificationScheduler")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        AndroidNotificationScheduler.onPermissionResult(requestCode, grantResults)
    }
}
