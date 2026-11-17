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

    api("org.joml:joml:$jomlVersion")
    api("com.google.code.gson:gson:$gsonVersion")

}
