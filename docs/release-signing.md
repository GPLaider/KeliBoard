# Release signing change in 4.0.2

KeliBoard 4.0.2 (4020) starts using a new release signing key. The original private key could not be recovered. This is a replacement key, with no signing lineage connecting it to the previous certificate.

Android rejects an in-place update from an older stable KeliBoard signed with the previous key. This affects GitHub installations and installations using the current F-Droid recipe: that recipe verifies reproducible builds and distributes the developer-signed APK instead of signing it with a separate F-Droid key.

## Before installing

1. Select another working keyboard so you can still type while KeliBoard is uninstalled.
2. In the old KeliBoard, open **Settings > Advanced > Backup and restore** and save a backup outside the app's private storage. Keep any separately imported files you need. Uninstalling deletes app data; a backup covers only the data supported by that feature.
3. Uninstall the old stable KeliBoard. Install the new APK, enable KeliBoard in Android's keyboard settings, and select it.
4. Restore the saved backup through **Backup and restore**, then check your languages, layouts, dictionaries, and other settings.

The package remains `io.github.gplaider.keliboard`. Debug builds and official HeliBoard use separate package IDs and do not need to be removed. Subsequent releases will use the new key so normal updates can resume.

## Certificate SHA-256 fingerprints

| Releases | Certificate SHA-256 |
| --- | --- |
| Previous stable releases, including 4.0.1 | `cda14ac8e6b7b73b73e025e60c9b650ed6cf82b472ad1a951b2ac826adf32276` |
| 4.0.2 and subsequent releases | `e359540ea1b8b4eba4f77887d89b8c7e9adb4c0bdee19c43241b7705c96b67b4` |

The public certificate is included with the GitHub release as `release-certificate.pem`. Verify a downloaded APK using Android SDK build-tools:

```text
apksigner verify --verbose --print-certs KeliBoard_4.0.2-GPLaider.apk
```

The F-Droid update proposal retains the previous certificate and adds the new certificate to `AllowedAPKSigningKeys`, allowing historical and new APKs to be checked. That allowlist does not grant Android update compatibility between the two keys. F-Droid availability depends on its maintainers accepting and publishing the update.

## 한국어 안내

4.0.2부터 릴리스 서명키가 바뀝니다. 이전 정식 버전 위에 바로 업데이트할 수 없습니다. 현재 F-Droid 등록도 개발자 서명을 재사용하므로 같은 제한이 적용됩니다.

먼저 다른 키보드를 사용할 수 있게 해 두고, 기존 KeliBoard의 **설정 > 고급 > 백업 및 복원**에서 백업 파일을 앱 외부에 저장하세요. 필요한 외부 사전 파일 등도 보관하세요. 기존 앱을 삭제하면 앱 데이터가 지워집니다. 4.0.2를 설치하고 키보드를 활성화한 다음 백업을 복원하고 언어·배열·사전 설정을 확인하세요. 백업은 해당 기능이 지원하는 데이터만 포함합니다.

이후 버전은 새 키를 유지해 일반 업데이트를 이어갑니다. 별도 패키지인 디버그 앱과 공식 HeliBoard를 삭제할 필요는 없습니다.
