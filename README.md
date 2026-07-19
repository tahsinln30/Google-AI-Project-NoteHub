# Android App

## Requirements

- Android Studio
- Gemini API Key

## Setup

1. Open the Project in Android Studio.
2. Create a `.env` File in the Project Folder.
3. Add Your API Key:

```env
GEMINI_API_KEY=your_api_key_here
```

4. If `build.gradle.kts` Contains the Following Line, Remove It:

```kotlin
signingConfig = signingConfigs.getByName("debugConfig")
```

5. Run the App on an Emulator or an Android Device.
