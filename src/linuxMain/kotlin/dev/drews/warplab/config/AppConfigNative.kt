package dev.drews.warplab.config

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv as posixGetenv

@OptIn(ExperimentalForeignApi::class)
internal actual fun getenv(name: String): String? = posixGetenv(name)?.toKString()
