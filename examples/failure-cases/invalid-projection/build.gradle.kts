// This example attempts to use an invalid projection. The build will fail.

plugins {
    `java-library`
    id("software.amazon.smithy").version("0.7.0")
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("software.amazon.smithy:smithy-model:[1.0, 2.0[")
}

smithy {
    sourceProjection.set("invalid")
    format.set(false)
}
