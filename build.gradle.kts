buildscript {
    repositories {
        gradleScriptKotlin()
    }

    dependencies {
        classpath(kotlinModule("gradle-plugin"))
    }
}

apply {
    plugin("kotlin")
    plugin("groovy")
}

repositories {
    gradleScriptKotlin()
    jcenter()
}

dependencies {
    compile(kotlinModule("stdlib"))

    testCompile("junit:junit:4.12")
    testCompile("org.hamcrest:hamcrest-library:1.3")

    testCompile("org.spockframework:spock-core:1.0-groovy-2.4")
    testRuntime("cglib:cglib-nodep:3.1")
    testRuntime("org.objenesis:objenesis:2.1")
}

