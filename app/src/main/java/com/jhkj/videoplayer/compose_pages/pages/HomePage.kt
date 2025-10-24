package com.jhkj.videoplayer.compose_pages.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jhkj.videoplayer.compose_pages.widgets.CustomIcon
import com.jhkj.videoplayer.compose_pages.widgets.RouterName

@Composable
fun HomeScreen(navController: NavController) {

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().height(54.dp)
            .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically) {
            Spacer( modifier = Modifier.fillMaxHeight().weight(1f))
            CustomIcon(
                modifier = Modifier.size(44.dp),
                imageVector = Icons.Outlined.Add,
                Color.Gray,
                Color.Blue,
            ){
                navController.navigate("${RouterName.AddConnection.name}")
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}
