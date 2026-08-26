# 账本提取器

一款面向家纺门店的纯离线 Android 订单管理应用。用户拍摄纸质账本后手动填写单号，订单图片和记录保存在本机。

## 功能

- 拍摄纸质账本并批量创建订单
- 按单号实时搜索
- 待取货、已取货订单分组管理
- 待取货订单备注及附加图片
- 标记取货时自动清理订单图片
- 已取货超过 15 天后自动清理完整记录
- 字体大小调节并在本地保存

## 技术栈

- Kotlin
- Jetpack Compose + Material 3
- Navigation Compose
- ViewModel + StateFlow
- Room
- Kotlin Coroutines

## 本地构建

1. 使用 Android Studio 打开项目。
2. 等待 Gradle 同步完成。
3. 连接 Android 8.0（API 26）或更高版本设备。
4. 运行 `app` 配置。

也可以在 Windows 终端执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

调试 APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## 数据与隐私

- 应用无登录、无云同步、无网络请求。
- 相机权限仅用于拍摄账本及订单附加图片。
- 订单和图片仅保存在设备本地。

## 发布说明

正式发布前请配置独立签名文件，并递增 `versionCode` 与 `versionName`。签名文件和本机 `local.properties` 已被 `.gitignore` 排除。
