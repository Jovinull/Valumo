// Build raiz: configuração comum aos módulos Java (engine, viewer).
// Versões fixadas aqui para reprodutibilidade (ver docs/validation/ambiente.md).

plugins {
    java
}

extra["lwjglVersion"] = "3.4.3"
extra["jomlVersion"] = "1.10.8"
extra["gsonVersion"] = "2.13.2"

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:unchecked")
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.11.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        workingDir = rootDir   // os testes de assets leem assets/ a partir da raiz
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

// Regenera todos os assets (modelos .vox, rigs, texturas) a partir dos scripts-fonte em tools/.
tasks.register<Exec>("assets") {
    group = "assets"
    description = "Gera assets/ a partir dos scripts Python em tools/ (determinístico)."
    workingDir = rootDir
    commandLine("python3", "tools/build_assets.py")
}
