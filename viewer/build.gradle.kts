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

// O viewer lê assets/ e escreve output/ relativos à raiz do repositório.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
