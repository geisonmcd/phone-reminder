plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
    id("com.google.firebase.crashlytics") version "3.0.6" apply false
}

subprojects {
    configurations.configureEach {
        resolutionStrategy.force(
            "androidx.datastore:datastore:1.1.7",
            "androidx.datastore:datastore-core:1.1.7",
            "androidx.datastore:datastore-core-okio:1.1.7",
            "androidx.datastore:datastore-preferences:1.1.7",
            "androidx.datastore:datastore-preferences-core:1.1.7",
            "androidx.datastore:datastore-preferences-external-protobuf:1.1.7",
            "androidx.datastore:datastore-preferences-proto:1.1.7",
        )
    }
}
