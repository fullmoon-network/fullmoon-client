plugins {
    // The no-remap variant. 26.1.2 ships unobfuscated: the version manifest has no
    // client_mappings, there is no yarn for it, and intermediary is the 0.0.0 stub — so the
    // short `fabric-loom` id fails at configuration time demanding a mappings artifact.
    id("net.fabricmc.fabric-loom") version "1.17.17"
    `java-library`
    jacoco
}

fun prop(name: String) = project.property(name) as String

val minecraftVersion = prop("minecraft_version")
val loaderVersion = prop("loader_version")
val fabricVersion = prop("fabric_version")
val modVersion = prop("mod_version")
val mavenGroup = prop("maven_group")
val archivesBaseName = prop("archives_base_name")

base { archivesName = archivesBaseName }
version = modVersion
group = mavenGroup

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
}

// i3 reaches the GUI submission point through an access widener rather than a mixin.
// i2 died on `@Inject … 'render' in Gui`; that method does not exist on 26.1.x, and a
// widened field cannot silently miss its target the way a descriptor-matched hook can.
loom {
    accessWidenerPath = file("src/main/resources/fullmoon.accesswidener")

    // A fixed window so a capture of a layout is comparable to the last capture of it. The size is
    // a property because a screen that outgrows one window has to be photographed in a bigger one,
    // and the capture rig is what knows which: tools/capture.py passes both.
    runs.named("client") {
        programArgs("--width", prop("client_width"), "--height", prop("client_height"))
        // The capture rig seeds a ledger account for one offline name; a random Player### would
        // join unlinked and spawn over the void the rig builds a platform for that name.
        providers.gradleProperty("client_username").orNull?.let { name ->
            programArgs("--username", name)
        }
        providers.gradleProperty("quick_play_server").orNull?.let { server ->
            programArgs("--quickPlayMultiplayer", server)
        }
        providers.gradleProperty("lobby").orNull?.let { lobby ->
            vmArg("-Dfullmoon.lobby=$lobby")
        }
        // A screen that is normally server-driven can only be photographed from a fixture.
        providers.gradleProperty("dev_screen").orNull?.let { screen ->
            vmArg("-Dfullmoon.devScreen=$screen")
        }
        // The concept mockup's row spacing, for photographing it beside the shipped spacing.
        providers.gradleProperty("density").orNull?.let { density ->
            vmArg("-Dfullmoon.density=$density")
        }
        // The capture rig re-renders the mockup with what the server actually sent, read from the log.
        providers.gradleProperty("menu_dump").orNull?.let { dump ->
            vmArg("-Dfullmoon.menuDump=$dump")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    // No mappings and no modImplementation: nothing is remapped, so the mod compiles
    // straight against the shipped jars on plain configurations.
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Reads the game's class files and the mixins to check that every injection target exists.
    testImplementation("org.ow2.asm:asm-tree:9.8")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing,-classfile", "-Werror"))
}

jacoco { toolVersion = "0.8.15" }

fun verifiedCoreClasses() = files(sourceSets.main.get().output.asFileTree.matching {
    include(
        "dev/fullmoon/client/hud/Anchor*",
        "dev/fullmoon/client/hud/HudGrid*",
        "dev/fullmoon/client/hud/HudWatch*",
        "dev/fullmoon/client/settings/SettingSearch*",
        "dev/fullmoon/client/network/BridgeProtocol*",
        "dev/fullmoon/client/network/BridgeState*",
        "dev/fullmoon/client/warp/WarpRoutes*",
        "dev/fullmoon/client/map/MapLayout*",
        "dev/fullmoon/client/map/MapViewport*",
        "dev/fullmoon/client/map/TerrainSnapshot*",
        "dev/fullmoon/client/map/TerrainSample.class",
        "dev/fullmoon/client/map/WorldNames*",
        "dev/fullmoon/client/map/MapMarkers*",
    )
})

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(verifiedCoreClasses())
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(verifiedCoreClasses())
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }

tasks.processResources {
    val props = mapOf(
        "version" to modVersion,
        "minecraft_version" to minecraftVersion,
        "loader_version" to loaderVersion,
        "fabric_version" to fabricVersion,
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

tasks.jar {
    from("LICENSE") { rename { "${it}_$archivesBaseName" } }
}
