plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.24"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly("org.spongepowered:mixin:0.8.7")
}

tasks {
    jar {
        manifest {
            attributes(mapOf(
                "MixinConfigs" to "moredatafix.mixins.json",
            ))
        }
    }
}
