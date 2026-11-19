plugins {
    `java-library`
}

val lwjglVersion = rootProject.extra["lwjglVersion"] as String
val jomlVersion = rootProject.extra["jomlVersion"] as String
val gsonVersion = rootProject.extra["gsonVersion"] as String

// Natives: Linux x64 é o alvo principal desta rodada. Para outro SO, troque o classificador.
val lwjglNatives = "natives-linux"

dependencies {
    api(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    api("org.lwjgl:lwjgl")
    api("org.lwjgl:lwjgl-glfw")
    api("org.lwjgl:lwjgl-opengl")
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    // Áudio (ADRs 0016 e 0023): OpenAL Soft (efeitos do combate desde a 09.1; mixagem, vozes posicionais, laço,
    // loopback para testes e gravação) e stb_vorbis (música em OGG). Módulos do próprio LWJGL, mesma versão do BOM; os
    // natives trazem o OpenAL Soft (LGPL, biblioteca dinâmica).
    api("org.lwjgl:lwjgl-openal")
    api("org.lwjgl:lwjgl-stb")
    runtimeOnly("org.lwjgl:lwjgl-openal::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")

    api("org.joml:joml:$jomlVersion")
    api("com.google.code.gson:gson:$gsonVersion")
}
