package com.github.tvbox.newbox.domain

interface BaseUseCase<in Params, out Result> {
    suspend operator fun invoke(params: Params): Result
}

interface NoParamUseCase<out Result> {
    suspend operator fun invoke(): Result
}
