plugins {
	kotlin("jvm")
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
	implementation(project(":Util"))
	implementation(project(":Multicore"))
	implementation(project(":Encryption"))
}

tasks.named("compileJava", JavaCompile::class.java) {
	options.compilerArgumentProviders.add(CommandLineArgumentProvider {
		// Provide compiled Kotlin classes to javac – needed for Java/Kotlin mixed sources to work
		listOf("--patch-module", "com.sterndu.JSONLib=${sourceSets["main"].output.asPath}") // , "--enable-preview"
	})
}