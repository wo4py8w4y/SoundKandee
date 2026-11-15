package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ChromaWaveScreen
import com.example.ui.ChromaWaveViewModel
import com.example.ui.theme.ChromaWaveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            ChromaWaveTheme {
                val vm: ChromaWaveViewModel = viewModel()
                ChromaWaveScreen(
                    viewModel = vm,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
