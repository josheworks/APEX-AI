# APEX AI MOBILE — INSTALLATION GUIDE (VIVO Y58 5G)

This guide walks you through installing and running the APEX AI mobile application on your **Vivo Y58 5G** (running Android 14 / Funtouch OS 14).

---

## 1. APK File Details

| Property | Value |
| :--- | :--- |
| **File Name** | `apex-mobile-debug.apk` |
| **Workspace Path** | `D:\APEX-MOBILE\apex-mobile-debug.apk` |
| **Gradle Build Output**| `D:\APEX-MOBILE\android\app\build\outputs\apk\debug\app-debug.apk` |
| **Package / App ID** | `com.apex.ai.mobile.debug` |
| **Version** | `0.1.0 (Code 1)` |
| **File Size** | **17,684,474 bytes (~16.86 MB)** |
| **Minimum Android** | Android 8.0+ (API 26) — Fully optimized for Android 14 (API 34) |
| **Target Device** | Vivo Y58 5G (Snapdragon 4 Gen 2, 8GB RAM, 120Hz Display) |

---

## 2. Transferring the APK to Your Vivo Y58 5G

Choose any of the following methods to transfer the APK to your phone:

### Method A: USB Cable (Fastest & Simplest)
1. Connect your Vivo Y58 5G to your PC via a USB-C cable.
2. On your phone screen, swipe down and change the USB mode from **"Charging only"** to **"File Transfer / Android Auto"**.
3. On your PC, open File Explorer, navigate to your phone's internal storage (e.g. `Vivo Y58 5G\Internal Storage\Download`).
4. Copy `D:\APEX-MOBILE\apex-mobile-debug.apk` into your phone's **Download** folder.

### Method B: Google Drive / Cloud Storage
1. Upload `apex-mobile-debug.apk` to your personal Google Drive or OneDrive.
2. Open the Google Drive app on your Vivo phone.
3. Tap on `apex-mobile-debug.apk` and tap **Download**.

### Method C: WhatsApp / Telegram to Yourself
1. Send the file as a **Document** to your own chat or saved messages.
2. On your phone, tap the received file to download and open it.

### Method D: ADB Direct Install (If USB Debugging is Enabled)
If Developer Options & USB Debugging are enabled on your Vivo phone:
```bash
adb install D:\APEX-MOBILE\apex-mobile-debug.apk
```

---

## 3. Installing on Vivo Y58 5G (Funtouch OS)

1. On your Vivo phone, open the pre-installed **"Files"** (or **"File Manager"**) app.
2. Tap **"Apps"** or open the **"Download"** folder.
3. Tap on **`apex-mobile-debug.apk`**.
4. **Funtouch OS Security Prompt:**
   - If prompted: *"For your security, your phone is not allowed to install unknown apps from this source"*, tap **Settings**.
   - Toggle **Allow from this source** to **ON**.
   - Press the Back button.
5. Tap **Install** on the APEX AI installation dialog.
6. Once installation completes, tap **Open**.

---

## 4. Permissions & Safety Configuration

APEX Mobile requests permissions **only when triggered by a specific task** (not all at startup):

| Permission | When Requested | Why It Is Needed |
| :--- | :--- | :--- |
| **Camera** | When asking APEX to take a photo or record video | Accesses the CameraX viewfinder to capture photos |
| **Microphone** | When recording voice notes or using the mic button | Audio recording for voice commands and audio clips |
| **Notifications** | When enabling incoming notification assistant | Allows APEX to read incoming message alerts in the status bar |
| **Accessibility Service** | Optional: when navigating screen UI | Inspects non-password visible screen nodes and assists with taps |
| **Storage / Files** | When reading or sharing files | Storage Access Framework (SAF) grants permission to selected files only |

### Optional: Enabling Accessibility Screen Assistant
If you want APEX to assist with navigating foreground screens:
1. Open **Settings** on your Vivo phone.
2. Go to **Shortcuts & Accessibility** → **Accessibility**.
3. Tap **APEX AI** and toggle it **ON**.
4. Confirm the system accessibility prompt.

---

## 5. Local AI Model Provisioning (FunctionGemma 270M)

The APK comes with an integrated **MockLocalModelAdapter** which enables immediate offline testing of all native device tools, safety dialogs, and workflows without requiring external downloads.

To run the real **Google FunctionGemma 270M** model on your phone:
1. Download the quantized LiteRT model artifact:
   - Target model: `google/functiongemma-270m-it` or `litert-community/functiongemma-270m-ft-mobile-actions`
   - File format: INT8 quantized `.litertlm` (e.g. `functiongemma-270m-it-q8_ekv1024.litertlm`, ~288 MB).
2. Transfer the file to the app's internal documents folder:
   `/Android/data/com.apex.ai.mobile.debug/files/models/`
3. In the APEX app, tap the **Settings** gear icon in the top right to verify that the model status indicates **READY (LiteRT-XNNPACK-CPU)**.

---

## 6. How to Test Core Features

When you open APEX on your phone, you'll see the PC Cyberpunk terminal interface adapted for mobile:

1. **Test Battery Check (Low Risk):**
   - Type or speak: `Check battery status`
   - APEX queries your device's `BatteryManager` and returns your battery percentage and charging state.
2. **Test App Control (Low Risk):**
   - Type: `Open WhatsApp` or `Open YouTube`
   - APEX launches the requested app using native Android launch intents.
3. **Test High-Risk Safety Confirmation (WhatsApp Message):**
   - Type: `Send Abi a message saying I will call later`
   - APEX displays the **Security Confirmation Dialog** with the recipient, message, and consequence warning.
   - Tap **CONFIRM ACTION** → APEX dispatches the message directly to WhatsApp with the recipient and text populated.
4. **Test Camera (Medium Risk):**
   - Type: `Take a photo`
   - APEX requests Camera permission and opens the camera capture viewfinder.
5. **Test Multi-Step Workflow:**
   - Tap Settings → Workflows → **Capture & Share Photo Workflow**.
   - APEX guides you through taking a photo and automatically preparing it in WhatsApp.

---

## 7. How to Uninstall

If you ever wish to remove the app:
1. Long-press the **APEX AI** icon on your home screen or app drawer.
2. Tap **App info** → **Uninstall**, or drag it to the **Uninstall** trash can.
3. Confirm uninstallation. All temporary app notes and cached data are automatically cleared by Android.
