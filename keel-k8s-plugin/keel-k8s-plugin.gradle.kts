plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  api(project(":keel-retrofit"))
  api("com.netflix.spinnaker.kork:kork-artifacts")
  api("com.fasterxml.jackson.module:jackson-module-kotlin")
  api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

  implementation(project(":keel-clouddriver"))
  implementation(project(":keel-core"))
  implementation(project(":keel-orca"))
  implementation("com.fasterxml.jackson.core:jackson-databind")
  implementation("com.fasterxml.jackson.core:jackson-annotations")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot-autoconfigure")

//  testImplementation(project(":keel-test"))
//  testImplementation(project(":keel-core-test"))
//  testImplementation("io.strikt:strikt-jackson")
//  testImplementation("dev.minutest:minutest")
//
//  testImplementation("org.assertj:assertj-core")
//  testImplementation("org.junit.jupiter:junit-jupiter-api")
//  testImplementation("org.junit.jupiter:junit-jupiter-params")
}
