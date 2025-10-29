package com.jhkj.videoplayer.compose_pages.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.jhkj.videoplayer.compose_pages.models.ConnInfo
import com.jhkj.videoplayer.compose_pages.viewmodel.ConnInfoVm
import com.jhkj.videoplayer.compose_pages.widgets.CustomIcon
import com.jhkj.videoplayer.compose_pages.widgets.RouterName
import com.jhkj.videoplayer.R
import com.jhkj.videoplayer.theme.textThird

@Composable
fun ConnectionsScreen(navController: NavController, viewModel: ConnInfoVm = viewModel()) {
    val connInfos by viewModel.allConn.collectAsState(initial = emptyList())

    Column(modifier = Modifier.padding(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(54.dp)
            .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically) {
            CustomIcon(
                modifier = Modifier.size(44.dp),
                imageVector = ImageVector.vectorResource(id = R.drawable.portrait),
                Color.Gray,
                Color.Blue,
            ){
                navController.navigate(RouterName.AddConnection.name)
            }
            Text(
                "连接",
                fontSize = 18.sp,
                fontWeight = FontWeight.W600,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            CustomIcon(
                modifier = Modifier.size(44.dp),
                imageVector = Icons.Outlined.Bolt,
                Color.Blue,
                Color.Blue,
            ){
                navController.navigate(RouterName.AddConnection.name)
            }
        }

        Row(modifier = Modifier.fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                "连接",
                fontSize = 14.sp,
                color = textThird,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn (modifier = Modifier.padding(horizontal = 6.dp)){
            items(connInfos) { conn ->
                ConnItem(conn = conn, onDelete = {
                    viewModel.deleteConn(conn) }
                )
            }
        }
    }
}

@Composable
fun ConnItem(conn: ConnInfo, onDelete: () -> Unit) {
    val img = connTypeList.find { conn.connType == it.identity.ordinal }!!

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = img.icon),
            contentDescription = "icon",
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = "${conn.domain}: ${conn.port}", modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}