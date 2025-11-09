plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // Даём Boot BOM управлять версиями
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.7"))

    implementation(project(":core"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation(kotlin("reflect"))                 // не указывай версию тут, её задаёт plugin
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

    // R2DBC + JDBC (JDBC нужен для Flyway)
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

// https://mvnrepository.com/artifact/org.postgresql/r2dbc-postgresql
    implementation("org.postgresql:r2dbc-postgresql:1.1.1.RELEASE")
// https://mvnrepository.com/artifact/io.r2dbc/r2dbc-pool
    implementation("io.r2dbc:r2dbc-pool:1.0.2.RELEASE")

    // Flyway и JDBC-драйвер
    implementation("org.flywaydb:flyway-core")                  // версию даст BOM
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")                    // JDBC-драйвер

    implementation("org.springframework.boot:spring-boot-starter-validation")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}