package com.jhkj.videoplayer.compose_pages.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.jhkj.videoplayer.R
import com.jhkj.videoplayer.compose_pages.widgets.CustomIcon
import com.jhkj.videoplayer.compose_pages.widgets.RouterName

enum class ConnType{
    WINDOWS,MACOS,LINUX,NAS,FTP,SFTP,WEBDAV,OWNCLOUD,NFS,GOOGLE_DRIVE,DROPBOX,ONEDRIVE,BOX,BAIDU_NETDISK,AWS,ALI_CLOUD,MEGA,JELLYFIN,EMBY
}
data class Item(val icon: Int, val title:String,val identity:ConnType)

val connTypeList = listOf(
    Item(R.drawable.windows,"Windows",ConnType.WINDOWS),
    Item(R.drawable.macos,"macOS",ConnType.MACOS),
    Item(R.drawable.linux,"Linux",ConnType.LINUX),
    Item(R.drawable.nas,"NAS",ConnType.NAS),
    Item(R.drawable.ftp,"FTP",ConnType.FTP),
    Item(R.drawable.sftp,"SFTP",ConnType.SFTP),
    Item(R.drawable.webdav,"WebDAV",ConnType.WEBDAV),
    Item(R.drawable.owncloud,"ownCloud",ConnType.OWNCLOUD),
    Item(R.drawable.nfs2,"NFS",ConnType.NFS),
    Item(R.drawable.google_drive,"Drive",ConnType.GOOGLE_DRIVE),
    Item(R.drawable.dropbox,"Dropbox",ConnType.DROPBOX),
    Item(R.drawable.onedrive,"OneDrive",ConnType.ONEDRIVE),
    Item(R.drawable.box,"Box",ConnType.BOX),
    Item(R.drawable.baidu_netdisk,"百度网盘",ConnType.BAIDU_NETDISK),
    Item(R.drawable.aws,"S3",ConnType.AWS),
    Item(R.drawable.ali_cloud,"阿里云盘",ConnType.ALI_CLOUD),
    Item(R.drawable.mega,"Mega",ConnType.MEGA),
    Item(R.drawable.jellyfin,"Jellyfin",ConnType.JELLYFIN),
    Item(R.drawable.emby,"Emby",ConnType.EMBY),
)

@Composable
fun AddConnectionScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(52.dp))

            Text(
                "新建连接",
                fontSize = 18.sp,
                fontWeight = FontWeight.W600,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            CustomIcon(
                modifier = Modifier.size(44.dp),
                imageVector = Icons.Outlined.Close,
                Color.Gray,
                Color.Gray,
                onClick = {
                    navController.popBackStack()
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        GridList(connTypeList,navController)
    }
}

@Composable
fun GridList(items: List<Item>,navController: NavController) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3), // 2列网格
        modifier = Modifier.fillMaxSize().padding(top = 10.dp, bottom = 10.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items.size, itemContent = {
            GridItem(items[it],navController)
        })
    }
}

@Composable
fun GridItem(item: Item,navController: NavController) {
//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .aspectRatio(1f), // 保持正方形
//        elevation = CardDefaults.elevatedCardElevation()
//    ) {
        Column(
            modifier = Modifier.padding(16.dp)
                .clickable {
                    //点击跳转到编辑页面
                    navController.popBackStack()
                    navController.navigate("${RouterName.EditConnection.name}/\"\"/true/${ConnType.WEBDAV.ordinal}")
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = item.icon),
                contentDescription = "icon",
                modifier = Modifier.size(48.dp)
            )
//            Icon(imageVector = item.icon, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = item.title, fontSize = 14.sp)
        }
//    }
}


//5. 交错网格布局 (Staggered Grid)
@Composable
fun StaggeredGridExample(items: List<Item>) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2), // 2列交错网格
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp
    ) {
        items(items.size) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp), // 不同高度
            ) {
                // 内容
            }
        }
    }
}

//6. 使用 Pager实现网格分页
@Composable
fun PagedGridExample(items: List<Item>) {
    val pager = rememberPagerState(pageCount = { (items.size + 5) / 6 }) // 每页6项
    HorizontalPager(state = pager) { page ->
        val pageItems = items.chunked(6).getOrElse(page) { emptyList() }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize()
        ) {
            items(pageItems.size) { index ->
//                GridItem(items[index],null)
            }
        }
    }
}