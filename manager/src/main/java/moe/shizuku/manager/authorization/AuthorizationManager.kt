package moe.shizuku.manager.authorization

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Parcel
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.Manifest
import moe.shizuku.manager.utils.Logger.LOGGER
import moe.shizuku.manager.utils.ShizukuSystemApis
import rikka.shizuku.server.ServerConstants
import rikka.parcelablelist.ParcelableListSlice
import rikka.shizuku.Shizuku
import java.util.*

object AuthorizationManager {

    private const val FLAG_ALLOWED = 1 shl 1
    private const val FLAG_DENIED = 1 shl 2
    private const val MASK_PERMISSION = FLAG_ALLOWED or FLAG_DENIED

    private fun isShizukuClientPackage(pi: PackageInfo): Boolean {
        if (BuildConfig.APPLICATION_ID == pi.packageName) return false
        if (pi.applicationInfo?.metaData?.getBoolean("moe.shizuku.client.V3_SUPPORT") != true) return false
        if (pi.requestedPermissions?.contains(Manifest.permission.API_V23) != true) return false
        return true
    }

    private fun getApplications(userId: Int): List<PackageInfo> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            data.writeInt(userId)
            try {
                Shizuku.getBinder()!!.transact(ServerConstants.BINDER_TRANSACTION_getApplications, data, reply, 0)
            } catch (e: Throwable) {
                throw RuntimeException(e)
            }
            reply.readException()
            @Suppress("UNCHECKED_CAST")
            (ParcelableListSlice.CREATOR.createFromParcel(reply) as ParcelableListSlice<PackageInfo>).list!!
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun getPackagesLegacyPath(): List<PackageInfo> {
        val packages: MutableList<PackageInfo> = ArrayList()
        val allPackages: MutableList<PackageInfo> = ArrayList()
        for (user in ShizukuSystemApis.getUsers(useCache = false)) {
            try {
                allPackages.addAll(
                    ShizukuSystemApis.getInstalledPackages(
                        (PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS).toLong(),
                        user.id
                    )
                )
            } catch (e: Throwable) {
                LOGGER.w(e, "getInstalledPackages")
            }
        }
        for (pi in allPackages) {
            if (isShizukuClientPackage(pi)) {
                packages.add(pi)
            }
        }
        return packages
    }

    fun getPackages(): List<PackageInfo> {
        if (Shizuku.isPreV11() || (Shizuku.getVersion() == 11 && Shizuku.getServerPatchVersion() < 3)) {
            return getPackagesLegacyPath()
        }

        return try {
            // Prefer server-side query for modern versions and fall back for newer Android edge cases.
            getApplications(-1).filter { isShizukuClientPackage(it) }
        } catch (e: Throwable) {
            LOGGER.w(e, "getApplications, fallback to getInstalledPackages")
            getPackagesLegacyPath()
        }
    }

    fun granted(packageName: String, uid: Int): Boolean {
        return if (Shizuku.isPreV11()) {
            ShizukuSystemApis.checkPermission(Manifest.permission.API_V23, packageName, uid / 100000) == PackageManager.PERMISSION_GRANTED
        } else {
            (Shizuku.getFlagsForUid(uid, MASK_PERMISSION) and FLAG_ALLOWED) == FLAG_ALLOWED
        }
    }

    fun grant(packageName: String, uid: Int) {
        if (Shizuku.isPreV11()) {
            ShizukuSystemApis.grantRuntimePermission(packageName, Manifest.permission.API_V23, uid / 100000)
        } else {
            Shizuku.updateFlagsForUid(uid, MASK_PERMISSION, FLAG_ALLOWED)
        }
    }

    fun revoke(packageName: String, uid: Int) {
        if (Shizuku.isPreV11()) {
            ShizukuSystemApis.revokeRuntimePermission(packageName, Manifest.permission.API_V23, uid / 100000)
        } else {
            Shizuku.updateFlagsForUid(uid, MASK_PERMISSION, 0)
        }
    }
}
