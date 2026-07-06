package com.rawtracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import io.github.ismoy.imagepickerkmp.domain.extensions.loadBytes
import io.github.ismoy.imagepickerkmp.features.imagepicker.config.ImagePickerKMPConfig
import io.github.ismoy.imagepickerkmp.features.imagepicker.model.ImagePickerResult
import io.github.ismoy.imagepickerkmp.features.imagepicker.ui.rememberImagePickerKMP

/**
 * Wraps ImagePickerKMP so the rest of the app only deals with raw [ByteArray] photos.
 */
@Composable
fun rememberFoodPicker(onImages: (List<ByteArray>) -> Unit): FoodPicker {
    val picker = rememberImagePickerKMP(config = ImagePickerKMPConfig())
    val handled = remember { mutableSetOf<Int>() }
    val result = picker.result
    LaunchedEffect(result) {
        if (result is ImagePickerResult.Success) {
            val photos = result.photos
            if (photos.isNotEmpty() && handled.add(result.hashCode())) {
                val bytes = photos.mapNotNull { photo ->
                    runCatching { photo.loadBytes() }.getOrNull()
                }
                if (bytes.isNotEmpty()) onImages(bytes)
            }
        }
    }
    return remember(picker) {
        FoodPicker(
            launchCamera = { picker.launchCamera() },
            launchGallery = { picker.launchGallery(allowMultiple = true, selectionLimit = 4) }
        )
    }
}

class FoodPicker(
    val launchCamera: () -> Unit,
    val launchGallery: () -> Unit
)
