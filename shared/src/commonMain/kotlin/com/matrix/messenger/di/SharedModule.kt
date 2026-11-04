package com.matrix.messenger.di

import com.matrix.messenger.data.repository.CallRepository
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.data.repository.MatrixRepositoryImpl
import com.matrix.messenger.ui.call.CallViewModel
import com.matrix.messenger.ui.chat.ChatViewModel
import com.matrix.messenger.ui.spaces.SpacesViewModel
import com.matrix.messenger.ui.home.HomeViewModel
import com.matrix.messenger.ui.login.LoginViewModel
import com.matrix.messenger.ui.rooms.RoomsCatalogViewModel
import com.matrix.messenger.ui.search.SearchViewModel
import com.matrix.messenger.ui.settings.DevicesViewModel
import com.matrix.messenger.ui.settings.SettingsViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val sharedModule = module {
    singleOf<MatrixRepository>(::MatrixRepositoryImpl)
    // Explicit lambda (not singleOf): the engine is a default constructor parameter
    // and must not be resolved by Koin.
    single { CallRepository(get()) }
    viewModelOf(::LoginViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ChatViewModel)
    viewModelOf(::CallViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::DevicesViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::RoomsCatalogViewModel)
    viewModel { (roomId: String) ->
        com.matrix.messenger.ui.room.RoomSettingsViewModel(get(), roomId)
    }
    viewModel { (roomId: String, rootId: String) ->
        com.matrix.messenger.ui.chat.ThreadViewModel(get(), roomId, rootId)
    }
    viewModelOf(::SpacesViewModel)
    viewModel { (spaceId: String) ->
        com.matrix.messenger.ui.spaces.SpaceDetailViewModel(get(), spaceId)
    }
}
