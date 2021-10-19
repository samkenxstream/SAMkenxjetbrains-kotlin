/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.symbols

import org.jetbrains.kotlin.analysis.api.fir.FirFrontendApiTestConfiguratorService
import org.jetbrains.kotlin.analysis.api.impl.base.test.symbols.AbstractSymbolByPsiTest

abstract class AbstractFirSymbolByPsiTest : AbstractSymbolByPsiTest(FirFrontendApiTestConfiguratorService)