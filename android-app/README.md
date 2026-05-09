# Notify Relay Android

Android MVP на Kotlin, Gradle Kotlin DSL и Jetpack Compose.

Текущая версия: `1.1.1 (3)`.

Правило версионирования: при каждом APK, который отдаётся на установку/тест, увеличивайте `versionCode`; `versionName` повышайте по смыслу изменений.

## Запуск

Откройте папку `android-app` в Android Studio или выполните сборку Gradle, если Gradle установлен:

```bash
gradle :app:assembleDebug
```

Для локального mock-сервера в debug-сборке используйте:

```text
http://10.0.2.2:8000
```

Release-сборка принимает только HTTPS backend URL.
