plugins {
    application
}

dependencies {
    implementation(project(":engine"))
}

application {
    mainClass.set("vx.viewer.Main")
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Djava.awt.headless=true"
    )
}

// O viewer lê assets/ e escreve output/ relativos à raiz do repositório; os assets gerados são criados antes
// (tarefa raiz :assets, pulada quando nada em tools/ mudou).
tasks.named<JavaExec>("run") {
    workingDir = rootDir
    dependsOn(":assets")
}
tasks.named("installDist") { dependsOn(":assets") }

// Rodada 07.2: criador de personagens do jogador (interface clicável, cena dedicada, miniaturas reais).
tasks.register<JavaExec>("editor") {
    group = "application"
    description = "Abre o criador de personagens (interface do jogador)."
    dependsOn(":assets")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("vx.viewer.Main")
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true")
    args("--editor")
    workingDir = rootDir
}

// Rodada 07: editor técnico de depuração (modo PERSONALIZACAO: texto, teclado, métricas).
tasks.register<JavaExec>("editorDebug") {
    group = "application"
    description = "Abre o editor técnico de personalização (depuração, rodada 07)."
    dependsOn(":assets")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("vx.viewer.Main")
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true")
    args("--editor-debug")
    workingDir = rootDir
}
