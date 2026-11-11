# AndroidX Test uses optional static-analysis annotations that have no runtime behavior.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.MustBeClosed
# JUnit and AndroidX Test discover runners, test methods and rules through reflection.
-keepclasseswithmembers class androidx.test.** { *; }
-keepclasseswithmembers class org.junit.** { *; }
-keepclasseswithmembers class junit.** { *; }
-keepclasseswithmembers @org.junit.runner.RunWith class * { *; }
-keepattributes *Annotation*
