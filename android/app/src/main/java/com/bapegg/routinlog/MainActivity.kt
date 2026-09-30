package com.bapegg.routinlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bapegg.routinlog.data.SystemStatusRepository
import com.bapegg.routinlog.ui.RoutineLogApp
import com.bapegg.routinlog.ui.RoutineLogViewModel
import com.bapegg.routinlog.ui.AccountViewModel
import com.bapegg.routinlog.data.AccountRepository
import com.bapegg.routinlog.ui.theme.RoutineLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = viewModelFactory {
            initializer {
                RoutineLogViewModel(SystemStatusRepository.create(BuildConfig.API_BASE_URL, BuildConfig.DEBUG))
            }
            initializer {
                AccountViewModel(AccountRepository.create(applicationContext, BuildConfig.API_BASE_URL,
                    BuildConfig.DEBUG, BuildConfig.GOOGLE_WEB_CLIENT_ID))
            }
        }
        setContent {
            RoutineLogTheme {
                val model: RoutineLogViewModel = viewModel(factory = factory)
                val account: AccountViewModel = viewModel(factory = factory)
                RoutineLogApp(model, if (BuildConfig.DEBUG) intent.getStringExtra("preview_route") else null, account)
            }
        }
    }
}
