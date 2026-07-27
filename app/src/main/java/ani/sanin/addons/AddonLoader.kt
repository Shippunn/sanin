package ani.sanin.addons

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import ani.sanin.util.Logger
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.extension.util.ExtensionLoader

class AddonLoader {
    companion object {

        /**
         * Load an extension from a package name with a specific class name
         * @param context the context
         * @param packageName the package name of the extension
         * @param type the type of extension
         * @return the loaded extension
         * @throws IllegalStateException if the extension is not of the correct type
         * @throws ClassNotFoundException if the extension class is not found
         * @throws NoClassDefFoundError if the extension class is not found
         * @throws Exception if any other error occurs
         * @throws PackageManager.NameNotFoundException if the package is not found
         * @throws IllegalStateException if the extension is not found
         */
        fun loadExtension(
            context: Context,
            packageName: String,
            className: String
        ): LoadResult? {
            val pkgManager = context.packageManager

            val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(ExtensionLoader.PACKAGE_FLAGS.toLong()))
            } else {
                pkgManager.getInstalledPackages(ExtensionLoader.PACKAGE_FLAGS)
            }

            val extPkgs = installedPkgs.filter {
                isPackageAnExtension(
                    packageName,
                    it
                )
            }

            if (extPkgs.isEmpty()) return null
            if (extPkgs.size > 1) throw IllegalStateException("Multiple extensions with the same package name found")

            val pkgName = extPkgs.first().packageName
            val pkgInfo = extPkgs.first()

            val appInfo = try {
                pkgManager.getApplicationInfo(pkgName, PackageManager.GET_META_DATA)
            } catch (error: PackageManager.NameNotFoundException) {
                // Unlikely, but the package may have been uninstalled at this point
                Logger.log(error)
                throw error
            }

            val extName =
                pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Sanin: ")
            val versionName = pkgInfo.versionName
            val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

            if (versionName.isNullOrEmpty()) {
                Logger.log("Missing versionName for extension $extName")
                throw IllegalStateException("Missing versionName for extension $extName")
            }
            val classLoader =
                PathClassLoader(appInfo.sourceDir, appInfo.nativeLibraryDir, context.classLoader)
            val loadedClass = try {
                Class.forName(className, false, classLoader)
            } catch (e: ClassNotFoundException) {
                Logger.log("ClassNotFoundException load error: $extName ($className)")
                Logger.log(e)
                throw e
            } catch (e: NoClassDefFoundError) {
                Logger.log("NoClassDefFoundError load error: $extName ($className)")
                Logger.log(e)
                throw e
            } catch (e: Exception) {
                Logger.log("Extension load error: $extName ($className)")
                Logger.log(e)
                throw e
            }
            val instance = loadedClass.getDeclaredConstructor().newInstance()

            return null
        }

        /**
         * Load an extension from a package name (class is determined by type)
         * @param context the context
         * @param packageName the package name of the extension
         * @param type the type of extension
         * @return the loaded extension
         */
        fun loadFromPkgName(context: Context, packageName: String): LoadResult? {
            return try {
                loadExtension(
                    context,
                    packageName,
                    ""
                )
            } catch (e: Exception) {
                Logger.log("Error loading extension from package name: $packageName")
                Logger.log(e)
                null
            }
        }

        /**
         * Check if a package is an extension by comparing the package name
         * @param type the type of extension
         * @param pkgInfo the package info
         * @return true if the package is an extension
         */
        private fun isPackageAnExtension(type: String, pkgInfo: PackageInfo): Boolean {
            return pkgInfo.packageName.equals(type)
        }
    }

}