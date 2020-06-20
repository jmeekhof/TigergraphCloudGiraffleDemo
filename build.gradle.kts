import com.optum.giraffle.UriScheme
import com.optum.giraffle.tasks.GsqlTask
import com.optum.giraffle.tasks.GsqlTokenDeleteTask
import com.optum.giraffle.tasks.GsqlTokenTask
import io.github.httpbuilderng.http.HttpTask // <1>

buildscript {
    dependencies {
        classpath("com.opencsv:opencsv:3.8")
    }
}

plugins {
    id("com.optum.giraffle") version "1.3.4.1"
    id("net.saliman.properties") version "1.5.1"
    id("io.github.http-builder-ng.http-plugin") version "0.1.1"
}

repositories {
    jcenter()
}

dependencies {
    gsqlRuntime("com.tigergraph.client:gsql_client:2.6.0")
}

http {
    config {
        it.request.setUri("$gHostUriType://$gHost:$gRestPort")
        it.request.headers["Authorization"] = "Bearer ${tigergraph.token.get()}" // <2.1>
    }
}

val gAdminPassword: String by project
val gAdminUserName: String by project
val gCertPath: String? by project
val gClientVersion: String? by project // <3>
val gGraphName: String by project
val gHost: String by project
val gHostUriType: String by project
val gPassword: String by project
val gRestPort: String by project
val gSecret: String? by project
val gUserName: String by project

val tokenMap: LinkedHashMap<String, String> = linkedMapOf(
    "graphname" to gGraphName
)

val schemaGroup: String = "Schema Tasks"
val loadingGroup: String = "Loading Tasks"
val queryGroup: String = "Query Tasks"

tigergraph {
    adminPassword.set(gAdminPassword)
    adminUserName.set(gAdminUserName)
    graphName.set(gGraphName)
    password.set(gPassword)
    scriptDir.set(file("db_scripts"))
    serverName.set(gHost)
    tokens.set(tokenMap)
    uriScheme.set(UriScheme.HTTPS)
    userName.set(gUserName)
    gClientVersion?.let {
        gsqlClientVersion.set(it)
    }
    gCertPath?.let {
        caCert.set(it)
    }
    gSecret?.let {
        authSecret.set(it)
    }
    logDir.set(file("./logs"))
}

tasks {
    wrapper {
        gradleVersion = "6.5"
    }

    register<GsqlTask>("showSchema") {
        scriptCommand = "ls"
        group = schemaGroup
        description = "Run simple `ls` command to display vertices, edges, and jobs for current graph"
    }

    register<GsqlTask>("nukeSchema") {
        scriptCommand = "drop all"
        group = schemaGroup
        description = "Executes a DROP ALL"
    }

    register<GsqlTask>("createSchema") {
        scriptPath = "schema/schema.gsql"
        useGlobal = true
        group = schemaGroup
        description = "Runs gsql to create a schema"
    }

    register<GsqlTask>("dropSchema") {
        scriptPath = "drop.gsql"
        group = schemaGroup
        description = "Runs gsql to drop the database schema"
        useGlobal = true
    }

    register<GsqlTask>("createLoadOrganisation") {
        scriptPath = "load/create/loadOrganisation.gsql"
        group = loadingGroup
        description = "Creates loading job for loading organisations"
    }

    register<GsqlTask>("createLoadFinanciers") {
        scriptPath = "load/create/loadFinanciers.gsql"
        group = loadingGroup
        description = "Creates loading job for loading financiers"
    }

    register<GsqlTask>("createLoadShareholdings") {
        scriptPath = "load/create/loadShareholdings.gsql"
        group = loadingGroup
        description = "Creates loading job for loading shareholdings"
    }

   register<GsqlTask>("createQueryGetOrganisation") {
        scriptPath = "query/get_relationships.gsql"
        group = queryGroup
        description = "Creates query to get relationships"
    }

    register<GsqlTask>("createQueryGetFinanciers") {
        scriptPath = "query/get_financiers.gsql"
        group = queryGroup
        description = "Creates query to get financiers"
    }

    register<HttpTask>("getFinanciers") {
        get { httpConfig ->
            httpConfig.request.uri.setPath("/query/$gGraphName/get_financiers")
            httpConfig.request.uri.setQuery(
                mapOf(
                    "org" to "Trusts"
                )
            )
            httpConfig.response.success { fs, x ->
                println(fs)
                println(x)
                println("Success")
            }
        }
    }

    register<HttpTask>("loadOrganisation") {
        group = loadingGroup
        description = "Load data via the REST++ endpoint"
        post { httpConfig ->
            httpConfig.request.uri.setPath("/ddl/$gGraphName")
            httpConfig.request.uri.setQuery(
                mapOf(
                    "tag" to "loadOrganisation",
                    "filename" to "f1",
                    "sep" to ",",
                    "eol" to "\n"
                )
            )
            httpConfig.request.setContentType("text/csv")
            val stream = File("data/Organisations.csv").inputStream()
            httpConfig.request.setBody(stream)
        }
    }

    register<HttpTask>("loadFinanciers") {
        group = loadingGroup
        description = "Load data via the REST++ endpoint"
        post { httpConfig ->
            httpConfig.request.uri.setPath("/ddl/$gGraphName")
            httpConfig.request.uri.setQuery(
                mapOf(
                    "tag" to "loadFinanciers",
                    "filename" to "f1",
                    "sep" to ",",
                    "eol" to "\n"
                )
            )
            httpConfig.request.setContentType("text/csv")
            val stream = File("data/Financiers.csv").inputStream()
            httpConfig.request.setBody(stream)
        }
    }

    register<HttpTask>("loadShareholdings") {
        group = loadingGroup
        description = "Load data via the REST++ endpoint"
        post { httpConfig ->
            httpConfig.request.uri.setPath("/ddl/${gGraphName}")
            httpConfig.request.uri.setQuery(
                mapOf(
                    "tag" to "loadShareholdings",
                    "filename" to "f1",
                    "sep" to ",",
                    "eol" to "\n"
                )
            )
            httpConfig.request.setContentType("text/csv")
            val stream = File("data/Shares.csv").inputStream()
            httpConfig.request.setBody(stream)
        }
    }

    val getToken by registering(GsqlTokenTask::class) {
        uriScheme.set(tigergraph.uriScheme.get())
        host.set(tigergraph.serverName.get())
        defaultPort.set(tigergraph.restPort.get())
    }

    register<GsqlTokenDeleteTask>("deleteToken") {}

    register<HttpTask>("getVersion") {
        description = "Get the server version from Tigergraph"
        get {
            it.request.uri.setPath("/version")
            it.response.success { fs, x ->
                println(fs)
                println(x)
                println("Success")
            }
        }
    }

    withType<HttpTask>().configureEach { // <6>
        dependsOn(getToken)
    }
}

val allCreateLoad by tasks.registering {
    group = loadingGroup
    description = "Creates all load rules -- as long as they all start with \"createLoad\"."
}

allCreateLoad {
    dependsOn(
        provider {
            tasks.filter { task -> task.name.startsWith("createLoad") }
        }
    )
}

val allLoad by tasks.registering {
    group = loadingGroup
    description = "load all  data -- as long as they start with \"load\"."
}

allLoad {
    dependsOn(
        provider {
            tasks.filter { task -> task.name.startsWith("load") }
        }
    )
}
