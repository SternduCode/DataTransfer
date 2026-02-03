plugins {
	alias(libs.plugins.kotlinJvm)
}

sourceSets.main {
	java.srcDirs("src")
}

java {
	modularity.inferModulePath.set(true)
}

kotlin {
	jvmToolchain(libs.versions.jvm.get().toInt())
	compilerOptions {
		freeCompilerArgs.add("-jvm-default=enable")
	}
	sourceSets.main {
		kotlin.srcDirs("src")
	}
}

dependencies {
	implementation(project(":Multicore"))
	implementation(project(":Encryption"))
	implementation(libs.ktor.serverCore)
	implementation(libs.ktor.serverNetty)
	implementation(libs.ktor.network)
	implementation(libs.ktor.network.tls)
	implementation(libs.ktor.client.core)
	implementation(libs.kotlinx.coroutinesSwing)
}

tasks.withType<Jar> {
	duplicatesStrategy = DuplicatesStrategy.WARN
}

tasks.named("compileJava", JavaCompile::class.java) {
	options.compilerArgumentProviders.add(CommandLineArgumentProvider {
		// Provide compiled Kotlin classes to javac – needed for Java/Kotlin mixed sources to work
		listOf("--patch-module", "com.sterndu.JSONLib=${sourceSets["main"].output.asPath}") // , "--enable-preview"
	})
}