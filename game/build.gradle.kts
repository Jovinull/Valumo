// Rodada 08: o jogo (regras de gameplay, jogador, entidades, experiência jogável). Reaproveita a engine e o
// runtime que hoje mora no módulo viewer (renderer, mundo, personagens, animação, compositor) — sem duplicar.
plugins {
    application
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":viewer"))
}

application {
    mainClass.set("vx.game.GameMain")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true")
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
    dependsOn(":assets")
}
tasks.named("installDist") { dependsOn(":assets") }

// ./gradlew jogo — abre o protótipo jogável (escolha de aparência → vila).
tasks.register<JavaExec>("jogo") {
    group = "application"
    description = "Abre o protótipo jogável do Valumo (exploração na vila)."
    dependsOn(":assets")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("vx.game.GameMain")
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true")
    workingDir = rootDir
}
