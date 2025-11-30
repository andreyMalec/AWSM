package com.malec.awsm

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.junit.After

abstract class KotlinPsiTest {
    private val disposable = Disposer.newDisposable()
    private val environment: KotlinCoreEnvironment

    init {
        val config = CompilerConfiguration()
        config.put(
            CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
            PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false)
        )
        val configFiles: EnvironmentConfigFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES
        environment = KotlinCoreEnvironment.createForProduction(disposable, config, configFiles)
    }

    protected val project: Project
        get() = environment.project

    protected fun parseFile(code: String, fileName: String = "Test.kt"): KtFile {
        return KtPsiFactory(project).createFile(fileName, code.trimIndent())
    }

    @After
    fun tearDownEnvironment() {
        Disposer.dispose(disposable)
    }
}

