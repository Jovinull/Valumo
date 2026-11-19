// Build raiz: configuração comum aos módulos Java (engine, viewer, game).
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
        systemProperty("vx.prefs", "-")   // rodada 09.2: testes nunca leem nem gravam as preferências do jogador
        systemProperty("vx.savegame", "-")   // rodada 10: nem o jogo salvo (teste de save usa pasta temporária)
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

// Regenera os assets GERADOS (.vox, rig.json, props.json, texturas, componentes, efeitos sonoros .wav) a partir dos scripts em tools/.
// Eles não vão para o git: o viewer, os testes e o installDist dependem desta tarefa, então um clone novo gera
// tudo sozinho. Entradas = scripts Python; saídas = pastas geradas — se nada em tools/ mudou e as saídas estão
// intactas, o Gradle pula a tarefa (UP-TO-DATE).
//
// Python: a síntese dos efeitos do combate (tools/audio_e.py) precisa de numpy; os demais módulos só usam a biblioteca
// padrão. Interpretador, nesta ordem: -Pvx.python=CAMINHO, variável VX_PYTHON, a .venv da raiz (se existir) e, por
// fim, o python3 do PATH. Assim o resultado não depende de a .venv estar "ativada" no terminal de quem chama.
// (As entradas são tools/*.py: build_assets.py só importa módulos do primeiro nível — auditoria de engenharia,
// output/qa/engineering-audit/. Se um módulo de subpasta entrar na geração, declare-o aqui.)
val generatedAssets = listOf("characters", "creatures", "environment", "textures", "studies", "components", "audio/sintese")
val assetsPython: String = (findProperty("vx.python") as String?)
    ?: System.getenv("VX_PYTHON")
    ?: listOf(".venv/bin/python3", ".venv/Scripts/python.exe").map { file(it) }.firstOrNull { it.isFile }?.path
    ?: "python3"
val assetsTask = tasks.register<Exec>("assets") {
    group = "assets"
    description = "Gera assets/ a partir dos scripts Python em tools/ (determinístico)."
    workingDir = rootDir
    commandLine(assetsPython, "tools/build_assets.py")
    inputs.files(fileTree("tools") { include("*.py") })
    generatedAssets.forEach { outputs.dir("assets/$it") }
}

subprojects {
    tasks.withType<Test>().configureEach {
        dependsOn(assetsTask)
        // Os testes leem assets/ (gerados + os escritos à mão: scenes, anim, catálogo e arquivos de áudio). Sem declarar
        // a pasta como entrada, editar assets/scenes/combate_e.json deixava `./gradlew test` em UP-TO-DATE com testes
        // que falhariam (auditoria de engenharia). Os gerados já chegam aqui pela dependência da tarefa :assets.
        inputs.dir(rootProject.layout.projectDirectory.dir("assets"))
            .withPropertyName("assets")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
