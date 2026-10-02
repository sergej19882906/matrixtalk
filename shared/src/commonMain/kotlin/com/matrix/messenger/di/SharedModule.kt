package com.matrix.messenger.di

import com.matrix.messenger.data.repository.CallRepository
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.data.repository.MatrixRepositoryImpl
import com.matrix.messenger.ui.call.CallViewModel
import com.matrix.messenger.ui.chat.ChatViewModel
import com.matrix.messenger.ui.home.HomeViewModel
import com.matrix.messenger.ui.login.LoginViewModel
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val sharedModule = module {
    singleOf<MatrixRepository>(::MatrixRepositoryImpl)
    singleOf(::CallRepository)
    viewModelOf(::LoginViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ChatViewModel)
    viewModelOf(::CallViewModel)
}
