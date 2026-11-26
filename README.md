# 5G Guard

Android app that watches your mobile network and, when it drops from 5G to 4G (or lower),
sounds a loud alert and cuts your internet. When 5G returns it turns the internet back on.

## Build and install
1. Open this folder in Android Studio (Koala or newer) and let Gradle sync.
2. Plug in your phone (USB debugging on) and press Run, or Build > Build APK(s).
3. Open the app, tap **Start monitoring**, allow phone state, notifications, and the VPN prompt.

## How it works
- `MonitorService` (foreground) listens to `TelephonyCallback.DisplayInfoListener` on Android 12+, which
  is the same signal behind the "5G" icon in your status bar, including 5G NSA.
- On 5G -> 4G/3G/2G it waits your chosen delay (default 5 s) so brief flicker doesn't trigger it,
  then fires the alert and starts `BlockVpnService`.
- `BlockVpnService` is a local VPN that swallows all traffic. Android does not let apps toggle
  mobile data directly, so this is the no-root way to go offline. The key icon in the status bar is normal.
- Wi-Fi is left alone: if you're on Wi-Fi when 5G drops, you only get the alert.

## Tips
- Exempt the app from battery optimisation (Settings > Apps > 5G Guard > Battery > Unrestricted)
  so the service isn't killed.
- Use **Test alert** and **Test internet cut** to check everything works.
- Android 11 and older can only tell standalone 5G from 4G, not 5G NSA.
- If the VPN is revoked or another VPN app takes over, the block ends.
