package com.bapegg.routinlog.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.bapegg.routinlog.data.ThumbnailDto
import com.bapegg.routinlog.ui.*
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@Composable internal fun PrivatePhoto(kind:String,id:String,label:String) {
    val model=LocalFeatures.current ?: return
    val owner=LocalAccount.current.state.userId ?: return
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var photo by remember(owner,kind,id){mutableStateOf<ThumbnailDto?>(null)}
    var error by remember(owner,kind,id){mutableStateOf<String?>(null)}
    var busy by remember(owner,kind,id){mutableStateOf(false)}
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(owner,kind,id,reload) {
        try{photo=model.photo(kind,id);error=null}catch(e:CancellationException){throw e}catch(_:Exception){error="사진을 불러오지 못했어요."}
    }
    val bitmap=remember(photo?.jpegBase64){photo?.jpegBase64?.let { runCatching { val bytes=Base64.decode(it,Base64.DEFAULT);BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.asImageBitmap() }.getOrNull() }}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri->
        if(uri!=null&&!busy&&photo!=null)scope.launch {
            busy=true;error=null
            try {
                val encoded=withContext(Dispatchers.IO) {
                    val bytes=context.contentResolver.openInputStream(uri)?.use { input->
                        ByteArrayOutputStream().use { output->
                            val buffer=ByteArray(8192)
                            while(true){val count=input.read(buffer);if(count<0)break;require(output.size()+count<=16*1024*1024);output.write(buffer,0,count)}
                            output.toByteArray()
                        }
                    } ?: error("사진 없음")
                    require(bytes.size<=16*1024*1024)
                    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                    require(bounds.outWidth in 1..20000&&bounds.outHeight in 1..20000)
                    var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>512)sample*=2
                    val input=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample }) ?: error("사진 형식")
                    val orientation=ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
                    val matrix=Matrix().apply { when(orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90->postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180->postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270->postRotate(270f)
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL->postScale(-1f,1f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL->postScale(1f,-1f)
                        ExifInterface.ORIENTATION_TRANSPOSE->{postRotate(90f);postScale(-1f,1f)}
                        ExifInterface.ORIENTATION_TRANSVERSE->{postRotate(270f);postScale(-1f,1f)}
                    } }
                    val oriented=Bitmap.createBitmap(input,0,0,input.width,input.height,matrix,true)
                    val scale=minOf(1f,256f/maxOf(oriented.width,oriented.height))
                    val small=Bitmap.createScaledBitmap(oriented,maxOf(1,(oriented.width*scale).toInt()),maxOf(1,(oriented.height*scale).toInt()),true)
                    val result=ByteArrayOutputStream().use { small.compress(Bitmap.CompressFormat.JPEG,85,it);Base64.encodeToString(it.toByteArray(),Base64.NO_WRAP) }
                    if(small!==oriented)small.recycle();if(oriented!==input)oriented.recycle();input.recycle();result
                }
                ensureActive();if(model.state.value.owner!=owner)throw CancellationException()
                photo=model.savePhoto(kind,id,ThumbnailDto(encoded,photo?.version))
            }catch(e:CancellationException){throw e}catch(_:Exception){error="사진을 저장하지 못했어요. 최신 사진을 다시 불러온 뒤 시도해주세요."}finally{busy=false}
        }
    }
    UiCard {
        if(bitmap!=null)Image(bitmap,label,Modifier.size(80.dp))else UiIcon(if(kind=="food")"Utensils"else"Dumbbell",Modifier.size(48.dp))
        MutedText("식별을 돕는 작은 대표 사진이에요. 원본 사진과 위치정보는 보관하지 않아요.")
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        UiButton("사진 선택",{launcher.launch("image/*")},false,!busy&&photo!=null)
        if(photo?.jpegBase64!=null)UiButton("사진 삭제",{scope.launch {
            busy=true;try{photo=model.savePhoto(kind,id,ThumbnailDto(version=photo?.version))}catch(e:CancellationException){throw e}catch(_:Exception){error="사진을 삭제하지 못했어요."}finally{busy=false}
        }},false,!busy)
        error?.let { Text(it);UiButton("사진 다시 불러오기",{reload++},false,!busy) }
    }
}
