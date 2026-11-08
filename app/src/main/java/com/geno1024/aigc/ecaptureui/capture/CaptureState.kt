package com.geno1024.aigc.ecaptureui.capture

sealed interface CaptureState {
    data object Idle : CaptureState
    data class Running(val pid: Int, val startedAt: Long, val outputFile: String) : CaptureState
    data class Error(val message: String) : CaptureState
}
