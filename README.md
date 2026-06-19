# Tactical-Legions
Tactical Legions is a mobile based Game that can be played with friends

# Debugging Game with only 1 device

## Steps

### 1. Build the APK for release:

```bash
.\gradlew.bat clean assembleRelease
```

### 2. Install the APK on your device:

```bash
adb install "app\build\outputs\apk\release\app-release.apk"
```

### 3. Start the game on your device:

```bash
adb shell am start -n com.activegames.tacticallegions/com.activegames.tacticallegions.MainActivity
```

Note: Ensure to have wscat installed in the laptop
```bash
# install from CMD or Powershell
npm install -g wscat

# Run wscat in a new terminal
# Ensure to have individual terminals for each player and ensure to change the player ID for each player
wscat -c ws://[IP_ADDRESS]:8080/game

# Pass the below JSON to join the Game
{"type": "com.activegames.tacticallegions.network.GameMessage.Join", "nickname": "Laptop-Player", "playerId": "laptop-unique-id-1"}

# Pass the below JSON to toggle Ready Status
{"type": "com.activegames.tacticallegions.network.GameMessage.ToggleReady", "playerId": "laptop-unique-id-1", "isReady": true}

