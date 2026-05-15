plugins {
    java
}

val gatlingVersion = "3.12.0"

dependencies {
    // Gatling
    testImplementation("io.gatling.highcharts:gatling-charts-highcharts:${gatlingVersion}")
    testImplementation("io.gatling:gatling-app:${gatlingVersion}")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.register<JavaExec>("gatlingRun") {
    group = "gatling"
    description = "Run all Gatling simulations"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.gatling.app.Gatling")
    args = listOf(
        "--simulation", "io.ten1010.loadtest.gatling.simulation.AllSimulations",
        "--results-folder", "${layout.buildDirectory.get()}/gatling-results"
    )
}
