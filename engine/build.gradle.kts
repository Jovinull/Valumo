plugins {
    `java-library`
}

val lwjglVersion: String by rootProject.extra
val jomlVersion: String by rootProject.extra
val gsonVersion: String by rootProject.extra

// Natives: Linux x64 é o alvo principal desta rodada. Para outro SO, troque o classificador.
val lwjglNatives = "natives-linux"

dependencies {
    api(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    api("org.lwjgl:lwjgl")
    api("org.lwjgl:lwjgl-glfw")
    api("org.lwjgl:lwjgl-opengl")
    api("org.lwjgl:lwjgl-openal")                 // rodada 09.1: efeitos sonoros (ADR 0016), mesmo projeto e versão do LWJGL
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    // Áudio (ADRs 0016 e 0023): OpenAL Soft (mixagem, vozes posicionais, laço, loopback para testes e gravação) e
    // stb_vorbis (música em OGG). Módulos do próprio LWJGL, mesma versão do BOM; os natives trazem o OpenAL Soft (LGPL,
    // biblioteca dinâmica).
    api("org.lwjgl:lwjgl-openal")
    api("org.lwjgl:lwjgl-stb")
    runtimeOnly("org.lwjgl:lwjgl-openal::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")

    api("org.joml:joml:$jomlVersion")
    api("com.google.code.gson:gson:$gsonVersion")

}
