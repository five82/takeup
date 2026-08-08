package xyz.five82.takeup.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** ViewModel wiring without a DI framework: the screen supplies the constructor. */
@Composable
inline fun <reified VM : ViewModel> takeupViewModel(
    key: String? = null,
    noinline create: () -> VM,
): VM = viewModel(key = key, factory = viewModelFactory { initializer { create() } })
