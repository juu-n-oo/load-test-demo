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

// ── 공통 Gatling 실행 설정 ─────────────────────────────────────────────────────

fun gatlingTask(
    taskName: String,
    simulationClass: String,
    description: String
): TaskProvider<JavaExec> {
    return tasks.register<JavaExec>(taskName) {
        group = "gatling"
        this.description = description
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("io.gatling.app.Gatling")
        args = listOf(
            "--simulation", simulationClass,
            "--results-folder", "${layout.buildDirectory.get()}/gatling-results"
        )
    }
}

// ── 개별 시뮬레이션 태스크 ─────────────────────────────────────────────────────

val gatlingRunUsers = gatlingTask(
    taskName = "gatlingRunUsers",
    simulationClass = "io.ten1010.loadtest.gatling.simulation.UserRegistrationSimulation",
    description = "사용자 가입 + 상품 조회 부하 테스트 실행"
)

val gatlingRunOrders = gatlingTask(
    taskName = "gatlingRunOrders",
    simulationClass = "io.ten1010.loadtest.gatling.simulation.OrderFlowSimulation",
    description = "주문 프로세스 부하 테스트 실행"
)

val gatlingRunPosts = gatlingTask(
    taskName = "gatlingRunPosts",
    simulationClass = "io.ten1010.loadtest.gatling.simulation.PostCommentSimulation",
    description = "게시글/댓글 부하 테스트 실행"
)

// ── 전체 실행 태스크 (순차 실행) ──────────────────────────────────────────────

tasks.register("gatlingRunAll") {
    group = "gatling"
    description = "모든 Gatling 시뮬레이션 순차 실행"
    dependsOn(gatlingRunUsers, gatlingRunOrders, gatlingRunPosts)
}

// 순차 실행을 위한 의존성 설정
tasks.named("gatlingRunOrders") { mustRunAfter(gatlingRunUsers) }
tasks.named("gatlingRunPosts") { mustRunAfter(gatlingRunOrders) }
