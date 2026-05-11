# ProGuard rules for Mindful Intervention App

# Keep AccessibilityService
-keep class com.mindful.intervention.MindfulAccessibilityService { *; }

# Keep OverlayService
-keep class com.mindful.intervention.OverlayService { *; }

# Keep MainActivity
-keep class com.mindful.intervention.MainActivity { *; }

# Keep BootCompletedReceiver
-keep class com.mindful.intervention.BootCompletedReceiver { *; }
