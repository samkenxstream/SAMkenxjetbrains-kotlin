/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.konan.internal

import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.ide.util.TipAndTrickBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.impl.ExtensionComponentAdapter
import com.intellij.task.ProjectTaskRunner
import com.intellij.task.impl.JpsProjectTaskRunner
import org.jetbrains.kotlin.idea.KotlinPluginUtil
import java.util.function.BiPredicate
import java.util.function.Predicate
import kotlin.reflect.KClass

/**
 * @author Vladislav.Soroka
 */
class KotlinNativeIdeInitializer {

    private companion object {
        val PLUGINS_TO_UNREGISTER_TIP_AND_TRICKS = setOf(
                KotlinPluginUtil.KOTLIN_PLUGIN_ID.idString, // all tips & tricks that come from the main Kotlin plugin
                "org.intellij.intelliLang", // Java plugin specific
                "com.intellij.diagram" // Java plugin specific
        )
    }

    init {
        unregisterGroovyInspections()
        suppressIrrelevantTipsAndTricks()
        disableJps()
    }

    // There are groovy local inspections which should not be loaded w/o groovy plugin enabled.
    // Those plugin definitions should become optional and dependant on groovy plugin.
    // This is a temp workaround before it happens.
    private fun unregisterGroovyInspections() = unregisterExtensionInstances(LocalInspectionEP.LOCAL_INSPECTION) {
        it.groupDisplayName == "Kotlin" && it.language == "Groovy"
    }

    private fun suppressIrrelevantTipsAndTricks() = unregisterExtensionInstances(TipAndTrickBean.EP_NAME) {
        it.pluginId?.idString in PLUGINS_TO_UNREGISTER_TIP_AND_TRICKS
    }

    private fun disableJps() = unregisterExtensionClass(ProjectTaskRunner.EP_NAME, JpsProjectTaskRunner::class)

    private fun <T : Any> unregisterExtensionClass(extensionPointName: ExtensionPointName<T>, extensionClass: KClass<out T>) {
        val extensionPoint = Extensions.getRootArea().getExtensionPoint(extensionPointName)
        val extensionClassesToUnregister: Set<String> = extensionPoint.extensions.filter { extensionClass.isInstance(it) }.map { it::class.java.name }.toSet()

        val negatedPredicate = BiPredicate<String, ExtensionComponentAdapter> { className, _ ->
            className in extensionClassesToUnregister
        }.negate()

        extensionPoint.unregisterExtensions(negatedPredicate, false)
    }

    private fun <T : Any> unregisterExtensionInstances(extensionPointName: ExtensionPointName<T>, predicate: (T) -> Boolean) {
        val extensionPoint = Extensions.getRootArea().getExtensionPoint(extensionPointName)
        val negatedPredicate = Predicate<T> { predicate(it) }.negate()

        // TODO: how to avoid dependency on deprecated method?
        @Suppress("DEPRECATION")
        extensionPoint.unregisterExtensions(negatedPredicate)
    }
}
