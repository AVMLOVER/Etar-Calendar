plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ec4j.editorconfig)
    alias(libs.plugins.kotlin.compose)
}

editorconfig {
	excludes = listOf("metadata/**", "**/*.xml", "**/*.webp")
}

kotlin {
    jvmToolchain(21)
}

android {
	namespace = "ws.xsoh.etar"
	testNamespace = "com.android.calendar.tests"
	compileSdk = 37

	defaultConfig {
		// 24 (was 23): androidx.compose.material3:material3 1.5.0 declares minSdk 24. Android 6.0
		// is effectively extinct, so requiring 7.0 is a cheaper trade than forking the ripple lib.
		minSdk = 24
		targetSdk = 37
		versionCode = 59
		versionName = "2.0.1"
		applicationId = "ws.xsoh.etar"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildTypes {
		release {
			// TODO: could be enabled for ProGuard minimization
			isMinifyEnabled = false
			resValue(
				"string",
				"search_authority",
				defaultConfig.applicationId + ".CalendarRecentSuggestionsProvider"
			)
		}

		debug {
			isMinifyEnabled = false

			applicationIdSuffix = ".debug"
			resValue(
				"string",
				"search_authority",
				defaultConfig.applicationId + ".debug.CalendarRecentSuggestionsProvider"
			)
		}
	}

	buildFeatures {
        buildConfig = true
		viewBinding = true
		resValues = true
		compose = true
	}

	/*
	 * To sign release build, create file gradle.properties in ~/.gradle/ with this content:
	 *
	 * signingStoreLocation=/home/key.store
	 * signingStorePassword=xxx
	 * signingKeyAlias=alias
	 * signingKeyPassword=xxx
	 */
	val signingStoreLocation: String? by project
	val signingStorePassword: String? by project
	val signingKeyAlias: String? by project
	val signingKeyPassword: String? by project

	if (
		signingStoreLocation != null &&
		signingStorePassword != null &&
		signingKeyAlias != null &&
		signingKeyPassword != null
	) {
		println("Found sign properties in gradle.properties! Signing build…")

		signingConfigs {
			named("release").configure {
				storeFile = File(signingStoreLocation!!)
				storePassword = signingStorePassword
				keyAlias = signingKeyAlias
				keyPassword = signingKeyPassword
			}
		}

		buildTypes.named("release").get().signingConfig = signingConfigs.named("release").get()
	} else {
		/*
		 * No release keystore was configured. Sign the release build with the debug key so the
		 * APK is directly installable for testing; it is not minified, because R8 shrinking made
		 * the app crash and was dropped.
		 */
		buildTypes.named("release").get().signingConfig = signingConfigs.getByName("debug")
	}

	lint {
		lintConfig = file("lint.xml")
		// TODO: Resolve lint errors due to 363aa9c237a33e9e1a40bdfd9039dcaaa855a5a0
		abortOnError = false
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true

		sourceCompatibility(JavaVersion.VERSION_21)
		targetCompatibility(JavaVersion.VERSION_21)
	}

	useLibrary("android.test.base")
	useLibrary("android.test.mock")

	androidResources {
		generateLocaleConfig = true
	}

}

dependencies {

	// Core
	implementation(libs.androidx.core)
	implementation(libs.androidx.preference)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.constraintlayout)
	implementation(libs.google.android.material)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.concurrent.futures)
    testImplementation(libs.junit)
	testImplementation(libs.androidx.test.runner)

	coreLibraryDesugaring(libs.android.tools.desugar)

	// Coroutines
	implementation(libs.kotlinx.coroutines.android)

	// https://mvnrepository.com/artifact/org.dmfs/lib-recur
	implementation(libs.dmfs.lib.recur)

	// lifecycle
	implementation(libs.androidx.lifecycle.livedata)

	// ---- Material 3 Expressive (Compose) ------------------------------------------------
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.graphics)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.graphics.shapes)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	debugImplementation(libs.androidx.compose.ui.tooling)
}
