# Notify Relay Android

Android MVP на Kotlin, Gradle Kotlin DSL и Jetpack Compose.

Текущая версия: `1.5.3 (8)`.

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

## Подпись release APK

Создайте release keystore один раз и храните его вне git:

```bash
keytool -genkeypair -v -keystore notify-relay-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias notify-relay
```

Скопируйте пример настроек:

```bash
cp keystore.properties.example keystore.properties
```

Заполните `keystore.properties` локальным путём к `.jks` и паролями. Этот файл игнорируется git.

Сборка подписанного release APK:

```bash
./gradlew :app:assembleRelease
```

Если `keystore.properties` отсутствует, release signing не включается автоматически.

## Живучесть в фоне

Приложение сохраняет события локально в Room и отправляет их через WorkManager. Для повышения стабильности:

- upload ставится как expedited work, если Android позволяет;
- есть периодический flush очереди каждые 15 минут;
- после перезагрузки телефона или обновления приложения очередь планируется заново;
- отправка требует сеть и будет повторяться с exponential backoff.

На агрессивных прошивках всё равно желательно отключить battery optimization для Notify Relay и разрешить background activity/autostart.
