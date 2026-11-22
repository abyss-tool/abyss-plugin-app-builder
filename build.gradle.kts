// 根构建脚本：插件版本插件声明（版本号走 AppInfo 同款单一事实源 ——
// plugin.json "version"，构建脚本经 CI 门禁校验三处一致）。
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
}
