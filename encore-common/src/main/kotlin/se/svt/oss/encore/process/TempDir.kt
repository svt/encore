// SPDX-FileCopyrightText: 2024 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.process

import java.nio.file.Path
import kotlin.io.path.createTempDirectory

fun createTempDir(prefix: String): Path {
    val tmpdir = System.getenv("ENCORE_TMPDIR")
        ?: System.getProperty("java.io.tmpdir")
    return createTempDirectory(tmpdir?.let { Path.of(it) }, prefix)
}
