package com.soa.stocksecurity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.soa.stocksecurity.ui.navigation.AppNavigation
import com.soa.stocksecurity.ui.theme.StockSecurityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
private fun App() {
    StockSecurityTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = com.soa.stocksecurity.ui.theme.Cream,
        ) {
            AppNavigation()
        }
    }
}
