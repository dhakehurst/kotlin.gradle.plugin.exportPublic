[![Build Status](https://travis-ci.com/dhakehurst/net.akehurst.kotlin.export-public.svg?branch=master)](https://travis-ci.com/dhakehurst/net.akehurst.kotlin.export-public)
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/net/akehurst/kotlin/gradle/plugin/export-public/maven-metadata.xml.svg?colorB=007ec6&label=gradle%20plugin)](https://plugins.gradle.org/plugin/net.akehurst.kotlin.export-public)

# net.akehurst.kotlin.gradle.plugin
- exportPublic
- jsIntegration


Latest Kotlin uses DCE to minimise JS module code size.
Their suggestion is to add @JsExport annotation to all classes that you want to export.
I don't like this. I think anything marked with public visibility should be exported.
This plugin make that happen.


## Add the plugin

```
plugins {
    id("net.akehurst.kotlin.gradle.plugin.exportPublic") version("<latest version>")
}
```

By default it exports all public declarations.
To limit this add some configuration

```
exportPublic {
    exportPatterns.set(
        listOf(
            "<qualified name glob>",
            "some.package.name.**"
        )
    )
}
```
