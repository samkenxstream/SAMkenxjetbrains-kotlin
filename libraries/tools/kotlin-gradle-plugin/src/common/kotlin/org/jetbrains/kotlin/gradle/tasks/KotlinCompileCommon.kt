/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.gradle.tasks

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.*
import org.gradle.work.InputChanges
import org.gradle.work.NormalizeLineEndings
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2MetadataCompilerArguments
import org.jetbrains.kotlin.compilerRunner.GradleCompilerEnvironment
import org.jetbrains.kotlin.compilerRunner.OutputItemsCollectorImpl
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.internal.tasks.allOutputFiles
import org.jetbrains.kotlin.gradle.logging.GradleErrorMessageCollector
import org.jetbrains.kotlin.gradle.logging.GradlePrintingMessageCollector
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerArgumentsProducer
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerArgumentsProducer.CreateCompilerArgumentsContext
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerArgumentsProducer.CreateCompilerArgumentsContext.Companion.create
import org.jetbrains.kotlin.gradle.report.BuildReportMode
import org.jetbrains.kotlin.gradle.tasks.internal.KotlinMultiplatformCommonOptionsCompat
import org.jetbrains.kotlin.gradle.utils.toPathsArray
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class KotlinCompileCommon @Inject constructor(
    override val compilerOptions: KotlinMultiplatformCommonCompilerOptions,
    workerExecutor: WorkerExecutor,
    objectFactory: ObjectFactory
) : AbstractKotlinCompile<K2MetadataCompilerArguments>(objectFactory, workerExecutor),
    KotlinCompilationTask<KotlinMultiplatformCommonCompilerOptions>,
    KotlinCompilerArgumentsProducer,
    KotlinCommonCompile {

    init {
        compilerOptions.verbose.convention(logger.isDebugEnabled)
    }

    override val kotlinOptions: KotlinMultiplatformCommonOptions = KotlinMultiplatformCommonOptionsCompat(
        { this },
        compilerOptions
    )

    /**
     * Workaround for those "nasty" plugins that are adding 'freeCompilerArgs' on task execution phase.
     * With properties api it is not possible to update property value after task configuration is finished.
     *
     * Marking it as `@Internal` as anyway on the configuration phase, when Gradle does task inputs snapshot,
     * this input will always be empty.
     */
    @get:Internal
    internal var executionTimeFreeCompilerArgs: List<String>? = null

    override fun createCompilerArgs(): K2MetadataCompilerArguments =
        K2MetadataCompilerArguments()

    override fun setupCompilerArgs(args: K2MetadataCompilerArguments, defaultsOnly: Boolean, ignoreClasspathResolutionErrors: Boolean) {
        KotlinMultiplatformCommonCompilerOptionsHelper.fillDefaultValues(args)
        super.setupCompilerArgs(args, defaultsOnly = defaultsOnly, ignoreClasspathResolutionErrors = ignoreClasspathResolutionErrors)

        args.moduleName = this@KotlinCompileCommon.moduleName.get()

        if (expectActualLinker.get()) {
            args.expectActualLinker = true
        }

        if (defaultsOnly) return

        val classpathList = try {
            libraries.files.filter { it.exists() }.toMutableList()
        } catch (t: Throwable) {
            if (ignoreClasspathResolutionErrors) null else throw t
        }

        with(args) {
            classpath = classpathList?.joinToString(File.pathSeparator)
            destination = destinationDirectory.get().asFile.normalize().absolutePath

            friendPaths = this@KotlinCompileCommon.friendPaths.files.map { it.absolutePath }.toTypedArray()
            refinesPaths = refinesMetadataPaths.map { it.absolutePath }.toTypedArray()
        }

        KotlinMultiplatformCommonCompilerOptionsHelper.fillCompilerArguments(compilerOptions, args)

        val localExecutionTimeFreeCompilerArgs = executionTimeFreeCompilerArgs
        if (localExecutionTimeFreeCompilerArgs != null) {
            args.freeArgs = localExecutionTimeFreeCompilerArgs
        }
    }

    override fun createCompilerArguments(context: CreateCompilerArgumentsContext) = context.create<K2MetadataCompilerArguments> {
        contribute(KotlinCompilerArgumentsProducer.ArgumentType.Primitive) { args ->
            args.multiPlatform = multiPlatformEnabled.get()

            args.moduleName = this@KotlinCompileCommon.moduleName.get()

            args.pluginOptions = (pluginOptions.toSingleCompilerPluginOptions() + kotlinPluginData?.orNull?.options)
                .arguments.toTypedArray()

            if (reportingSettings().buildReportMode == BuildReportMode.VERBOSE) {
                args.reportPerf = true
            }

            args.expectActualLinker = expectActualLinker.get()

            args.destination = destinationDirectory.get().asFile.normalize().absolutePath

            KotlinCommonCompilerOptionsHelper.fillCompilerArguments(compilerOptions, args)

            val localExecutionTimeFreeCompilerArgs = executionTimeFreeCompilerArgs
            if (localExecutionTimeFreeCompilerArgs != null) {
                args.freeArgs = localExecutionTimeFreeCompilerArgs
            }
        }

        contribute(KotlinCompilerArgumentsProducer.ArgumentType.PluginClasspath) { args ->
            args.pluginClasspaths = tryLenient {
                listOfNotNull(
                    pluginClasspath, kotlinPluginData?.orNull?.classpath
                ).reduce(FileCollection::plus).toPathsArray()
            }
        }

        contribute(KotlinCompilerArgumentsProducer.ArgumentType.DependencyClasspath) { args ->
            args.classpath = tryLenient { libraries.files.filter { it.exists() }.joinToString(File.pathSeparator) }
            args.friendPaths = tryLenient { this@KotlinCompileCommon.friendPaths.files.toPathsArray() }
            args.refinesPaths = refinesMetadataPaths.toPathsArray()
        }

        contribute(KotlinCompilerArgumentsProducer.ArgumentType.Sources) { args ->
            args.freeArgs += sources.asFileTree.map { it.absolutePath }
            args.commonSources = commonSourceSet.asFileTree.toPathsArray()
        }
    }

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:InputFiles
    @get:NormalizeLineEndings
    internal val refinesMetadataPaths: ConfigurableFileCollection = objectFactory.fileCollection()

    @get:Internal
    internal val expectActualLinker = objectFactory.property(Boolean::class.java)

    override fun callCompilerAsync(
        args: K2MetadataCompilerArguments,
        kotlinSources: Set<File>,
        inputChanges: InputChanges,
        taskOutputsBackup: TaskOutputsBackup?
    ) {
        val gradlePrintingMessageCollector = GradlePrintingMessageCollector(logger, args.allWarningsAsErrors)
        val gradleMessageCollector = GradleErrorMessageCollector(gradlePrintingMessageCollector)
        val outputItemCollector = OutputItemsCollectorImpl()
        val compilerRunner = compilerRunner.get()
        val environment = GradleCompilerEnvironment(
            defaultCompilerClasspath, gradleMessageCollector, outputItemCollector,
            reportingSettings = reportingSettings(),
            outputFiles = allOutputFiles()
        )
        compilerRunner.runMetadataCompilerAsync(args, environment)
        compilerRunner.errorsFile?.also { gradleMessageCollector.flush(it) }
    }
}
