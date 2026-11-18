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
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    // Rodada 09 — áudio (ADR 0015): OpenAL Soft (mixagem, laço, loopback para testes) e stb_vorbis (OGG).
    // Módulos do próprio LWJGL, mesma versão do BOM; os natives trazem o OpenAL Soft embutido.
    api("org.lwjgl:lwjgl-openal")
    api("org.lwjgl:lwjgl-stb")
    runtimeOnly("org.lwjgl:lwjgl-openal::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")

    api("org.joml:joml:$jomlVersion")
    api("com.google.code.gson:gson:$gsonVersion")

}
