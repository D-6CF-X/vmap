package com.vmap.app.feature.spot

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vmap.app.VMapApp
import com.vmap.app.core.ui.accent
import com.vmap.app.domain.model.Spot
import com.vmap.app.feature.map.MapViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotEditSheet(
    spot: Spot,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: MapViewModel,
) {
    val context = LocalContext.current
    val app = context.applicationContext as VMapApp
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = spot.section.accent

    val photos by app.container.spotRepository
        .observePhotos(spot.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(9),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            uris.forEach { uri ->
                app.container.photoStore.import(uri, spot.id)?.let { photo ->
                    app.container.spotRepository.addPhoto(photo)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = spot.cityName,
                    color = accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                        .format(Date(spot.performedAt)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = spot.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
            )

            if (spot.venue.isNotBlank()) {
                Text(
                    text = spot.venue,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }

            if (spot.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = spot.note,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (photos.isEmpty()) {
                Text(
                    text = "还没有照片",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                ) {
                    items(photos, key = { it.id }) { photo ->
                        AsyncImage(
                            model = File(app.filesDir, photo.path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent.copy(alpha = 0.18f),
                    contentColor = accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("添加照片")
            }

            TextButton(
                onClick = {
                    scope.launch {
                        viewModel.delete(spot.id)
                        onDeleted()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("删除这次打卡", color = Color(0xFFE24B4A), fontSize = 13.sp)
            }
        }
    }
}
