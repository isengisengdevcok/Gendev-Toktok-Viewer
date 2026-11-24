package com.example

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26]) // Android 8.0 (minSdk target)
class ExampleRobolectricTest {

    @Test
    fun `manifest package permissions and app name compatibility`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GENDEV TikTok Viewer", appName)

        assertEquals("com.gendev.tiktokviewer", context.packageName)

        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = packageInfo.requestedPermissions?.toList().orEmpty()
        assertTrue(permissions.contains(android.Manifest.permission.INTERNET))
        assertTrue(permissions.contains(android.Manifest.permission.ACCESS_NETWORK_STATE))
        // Privacy verification: never request storage, location, contacts, or camera permissions
        assertFalse(permissions.contains(android.Manifest.permission.READ_EXTERNAL_STORAGE))
        assertFalse(permissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
        assertFalse(permissions.contains(android.Manifest.permission.READ_CONTACTS))
    }
}
