/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.rmi.service

import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.incremental.components.ScopeKind
import org.jetbrains.kotlin.rmi.CompileService
import org.jetbrains.kotlin.rmi.DummyProfiler
import org.jetbrains.kotlin.rmi.Profiler


class RemoteLookupTrackerClient(val proxy: CompileService.RemoteLookupTracker, val profiler: Profiler = DummyProfiler()) : LookupTracker {

    private val isDoNothing = profiler.withMeasure(this) { proxy.isDoNothing() }

    override fun record(lookupContainingFile: String, lookupLine: Int?, lookupColumn: Int?, scopeFqName: String, scopeKind: ScopeKind, name: String) {
        if (!isDoNothing) {
            profiler.withMeasure(this) {
                proxy.record(lookupContainingFile, lookupLine, lookupColumn, scopeFqName, scopeKind, name)
            }
        }
    }
}
